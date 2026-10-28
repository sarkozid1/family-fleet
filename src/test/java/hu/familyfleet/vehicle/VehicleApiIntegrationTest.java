package hu.familyfleet.vehicle;

import com.jayway.jsonpath.JsonPath;
import hu.familyfleet.TestcontainersConfiguration;
import hu.familyfleet.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.context.WebApplicationContext;
import hu.familyfleet.deadline.Deadline;
import hu.familyfleet.deadline.DeadlineRepository;
import hu.familyfleet.deadline.DeadlineType;
import hu.familyfleet.household.Household;
import hu.familyfleet.household.HouseholdRepository;
import hu.familyfleet.insurance.InsurancePolicy;
import hu.familyfleet.insurance.InsurancePolicyRepository;
import hu.familyfleet.insurance.InsurerRepository;
import hu.familyfleet.insurance.PaymentFrequency;
import hu.familyfleet.insurance.PolicyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Végponttól végpontig a valódi adatbázissal; minden teszt saját háztartást hoz létre. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class VehicleApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void authenticate() {
        mockMvc = TestAuth.mockMvcAsNewUser(context);
    }

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private InsurerRepository insurerRepository;

    @Autowired
    private InsurancePolicyRepository policyRepository;

    @Autowired
    private DeadlineRepository deadlineRepository;

    @Autowired
    private HouseholdRepository householdRepository;

    @Test
    void vehicleLifecycleWithOptimisticLocking() throws Exception {
        long householdId = createHousehold("Kiss család");
        long ownerId = createMember(householdId, "Kiss Anna");

        MvcResult created = mockMvc.perform(post("/api/households/{id}/vehicles", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "CAR", "brand": "Toyota", "model": "Corolla",
                                  "licensePlate": "aa-bc-123", "vin": "JTDBR32E720123456",
                                  "productionYear": 2021, "currentMileage": 45000, "ownerId": %d
                                }
                                """.formatted(ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("AABC123"))
                .andExpect(jsonPath("$.ownerId").value(ownerId))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String vehicleUrl = created.getResponse().getHeader("Location");

        mockMvc.perform(put(vehicleUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(50_000, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMileage").value(50_000))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.version").value(1));

        // Egy másik kliens még a 0-s verziót látta: a módosítása nem írhatja felül az előzőt.
        mockMvc.perform(put(vehicleUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(51_000, 0)))
                .andExpect(status().isConflict());

        mockMvc.perform(put(vehicleUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(40_000, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("nem csökkenhet")));

        mockMvc.perform(delete(vehicleUrl)).andExpect(status().isNoContent());
        mockMvc.perform(get(vehicleUrl)).andExpect(status().isNotFound());
    }

    @Test
    void duplicatePlateInSameHouseholdReturns409ButOtherHouseholdIsFine() throws Exception {
        long mine = createHousehold("Saját család");
        long other = createHousehold("Másik család");
        createVehicle(mine, "ABC-123");

        mockMvc.perform(post("/api/households/{id}/vehicles", mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson("abc 123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("ABC123")));

        mockMvc.perform(post("/api/households/{id}/vehicles", other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson("ABC123")))
                .andExpect(status().isCreated());
    }

    @Test
    void ownerFromAnotherHouseholdIsRejected() throws Exception {
        long mine = createHousehold("Saját család");
        long strangerId = createMember(createHousehold("Idegen család"), "Idegen Béla");

        mockMvc.perform(post("/api/households/{id}/vehicles", mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "CAR", "brand": "Skoda", "model": "Fabia",
                                  "licensePlate": "XYZ987", "ownerId": %d
                                }
                                """.formatted(strangerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("nem tagja")));
    }

    @Test
    void listIsPagedAndSortedByPlate() throws Exception {
        long householdId = createHousehold("Nagy család");
        createVehicle(householdId, "CCC333");
        createVehicle(householdId, "AAA111");
        createVehicle(householdId, "BBB222");

        mockMvc.perform(get("/api/households/{id}/vehicles", householdId).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].licensePlate", contains("AAA111", "BBB222")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/households/{id}/vehicles", householdId).param("sort", "licensePlate,desc"))
                .andExpect(jsonPath("$.content[0].licensePlate").value("CCC333"));

        mockMvc.perform(get("/api/households/{id}/vehicles", householdId).param("sort", "nincsIlyenMezo"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listOfUnknownHouseholdReturns404() throws Exception {
        mockMvc.perform(get("/api/households/{id}/vehicles", Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void vehicleWithPolicyOrDeadlineCannotBeDeleted() throws Exception {
        long householdId = createHousehold("Kötvényes család");
        long withPolicy = createVehicle(householdId, "PPP111");
        long withDeadline = createVehicle(householdId, "DDD222");

        Vehicle policyVehicle = vehicleRepository.findById(withPolicy).orElseThrow();
        policyRepository.save(new InsurancePolicy(policyVehicle, insurerRepository.findAll().getFirst(),
                PolicyType.KGFB, LocalDate.of(2026, 1, 1), new BigDecimal("52000"), PaymentFrequency.ANNUAL));
        Vehicle deadlineVehicle = vehicleRepository.findById(withDeadline).orElseThrow();
        // A tranzakción kívül betöltött jármű háztartása lazy proxy session nélkül, ezért külön töltjük be.
        Household household = householdRepository.findById(householdId).orElseThrow();
        Deadline deadline = new Deadline(household, DeadlineType.TECHNICAL_INSPECTION,
                LocalDate.of(2027, 4, 30), deadlineVehicle, null, null);
        deadlineRepository.save(deadline);

        mockMvc.perform(delete("/api/vehicles/{id}", withPolicy))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("kötvény")));
        mockMvc.perform(delete("/api/vehicles/{id}", withDeadline))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("határidő")));
    }

    private long createHousehold(String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s" }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long createMember(long householdId, String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/households/{id}/members", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s" }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long createVehicle(long householdId, String plate) throws Exception {
        return idOf(mockMvc.perform(post("/api/households/{id}/vehicles", householdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehicleJson(plate)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String vehicleJson(String plate) {
        return """
                { "type": "CAR", "brand": "Skoda", "model": "Octavia", "licensePlate": "%s" }
                """.formatted(plate);
    }

    private static String updateJson(int mileage, long version) {
        return """
                {
                  "type": "CAR", "brand": "Toyota", "model": "Corolla Touring",
                  "licensePlate": "AA-BC-123", "productionYear": 2021,
                  "currentMileage": %d, "version": %d
                }
                """.formatted(mileage, version);
    }
}
