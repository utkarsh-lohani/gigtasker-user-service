package com.gigtasker.userservice.seed;

import com.gigtasker.userservice.dto.RegistrationRequest;
import com.gigtasker.userservice.dto.RestCountry;
import com.gigtasker.userservice.entity.Country;
import com.gigtasker.userservice.entity.Gender;
import com.gigtasker.userservice.entity.Region;
import com.gigtasker.userservice.entity.SubRegion;
import com.gigtasker.userservice.enums.GenderType;
import com.gigtasker.userservice.enums.RoleType;
import com.gigtasker.userservice.exceptions.GenderNotFoundException;
import com.gigtasker.userservice.repository.CountryRepository;
import com.gigtasker.userservice.repository.GenderRepository;
import com.gigtasker.userservice.repository.RegionRepository;
import com.gigtasker.userservice.repository.SubRegionRepository;
import com.gigtasker.userservice.service.AuthService;
import com.gigtasker.userservice.service.KeycloakService;
import com.gigtasker.userservice.service.RestCountriesService;
import com.gigtasker.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationBootstrapSeeder implements CommandLineRunner {

    private final GenderRepository genderRepository;
    private final RegionRepository regionRepository;
    private final CountryRepository countryRepository;
    private final SubRegionRepository subRegionRepository;
    private final RestCountriesService restCountriesService;
    private final AuthService authService;
    private final UserService userService;
    private final KeycloakService keycloakService;

    @Value("${app.seeding.enabled:true}")
    private boolean seedingEnabled;

    @Value("${app.seeding.create-admin:true}")
    private boolean createAdmin;

    private static final String UNKNOWN = "Unknown";

    @Override
    public void run(String @NonNull ... args) {
        if (!seedingEnabled) {
            log.info("Seeding is disabled in config.");
            return;
        }
        seedKeycloakStructure();
        seedCountries();
        seedUsers();
    }

    private record SeedUserRequest(
            String username,
            String email,
            String firstName,
            String lastName,
            GenderType genderType,
            String countryCode,
            boolean isAdmin
    ) {}

    private void seedCountries() {

        if (countryRepository.count() > 0) {
            log.info("Countries already seeded");
            return;
        }

        List<RestCountry> apiCountries = restCountriesService.fetchCountries();

        Map<String, Region> regionMap = new HashMap<>();
        Map<String, SubRegion> subRegionMap = new HashMap<>();

        // Build regions and subregions
        for (RestCountry apiCountry : apiCountries) {

            String continent = apiCountry.getContinents() != null && !apiCountry.getContinents().isEmpty()
                    ? apiCountry.getContinents().getFirst() : UNKNOWN;

            String macroRegion = apiCountry.getRegion() != null ? apiCountry.getRegion() : UNKNOWN;

            String subRegionName = apiCountry.getSubregion() != null ? apiCountry.getSubregion() : UNKNOWN;

            regionMap.putIfAbsent(continent,
                    Region.builder()
                            .name(continent)
                            .macroRegion(macroRegion)
                            .build()
            );

            Region region = regionMap.get(continent);

            String key = continent + "___" + subRegionName;

            subRegionMap.putIfAbsent(key,
                    SubRegion.builder()
                            .name(subRegionName)
                            .region(region)
                            .build()
            );
        }

        // Save regions
        List<Region> savedRegions = regionRepository.saveAll(regionMap.values());
        Map<String, Region> regionLookup = savedRegions.stream()
                .collect(Collectors.toMap(Region::getName, r -> r));

        // Save subregions
        List<SubRegion> subRegions = new ArrayList<>();
        for (SubRegion s : subRegionMap.values()) {
            s.setRegion(regionLookup.get(s.getRegion().getName()));
            subRegions.add(s);
        }
        subRegionRepository.saveAll(subRegions);

        // Save countries (REGION ONLY)
        List<Country> countries = new ArrayList<>();

        for (RestCountry apiCountry : apiCountries) {

            String continent =
                    apiCountry.getContinents() != null && !apiCountry.getContinents().isEmpty()
                            ? apiCountry.getContinents().getFirst()
                            : UNKNOWN;

            Country country = Country.builder()
                    .name(apiCountry.getName().getCommon())
                    .isoCode(apiCountry.getCca2())
                    .phoneCode(extractPhoneCode(apiCountry))
                    .currencyCode(extractCurrencyCode(apiCountry))
                    .region(regionLookup.get(continent))
                    .build();

            countries.add(country);
        }

        countryRepository.saveAll(countries);
    }

    private String extractPhoneCode(RestCountry c) {
        if (c.getIdd() == null) return null;

        String root = c.getIdd().getRoot();
        List<String> suffixes = c.getIdd().getSuffixes();

        if (root == null) return null;
        if (suffixes == null || suffixes.isEmpty()) return root;

        return root + suffixes.getFirst();
    }

    private String extractCurrencyCode(RestCountry c) {
        if (c.getCurrencies() == null || c.getCurrencies().isEmpty()) return null;

        return c.getCurrencies().keySet().iterator().next();
    }

    private void seedUsers() {
        // 2. Define users clearly
        List<SeedUserRequest> usersToSeed = new ArrayList<>();

        if (createAdmin) {
            usersToSeed.add(new SeedUserRequest(
                    "admin_gigtasker",
                    "admin_gigtasker@yopmail.com",
                    "Super",
                    "Admin",
                    GenderType.MAN,
                    "US",
                    true // Is Admin
            ));
        }

        usersToSeed.add(new SeedUserRequest(
                "user_gigtasker",
                "user_gigtasker@yopmail.com",
                "Regular",
                "User",
                GenderType.WOMAN,
                "GB",
                false // Not Admin
        ));

        // 3. Process the list
        for (SeedUserRequest req : usersToSeed) {
            seedSingleUser(req);
        }
    }

    private void seedSingleUser(SeedUserRequest req) {
        if (userService.getUserByEmail(req.email()) != null) {
            log.info("User {} already exists. Skipping.", req.username());
            return;
        }

        log.info("Creating User: {}...", req.username());

        try {
            Gender gender = genderRepository.findByName(req.genderType())
                    .orElseThrow(() -> new GenderNotFoundException(req.genderType().name()));

            Country country = countryRepository.findByIsoCode(req.countryCode())
                    .orElseThrow(() -> new NoSuchElementException("Country '" + req.countryCode() + "' not found!"));

            RegistrationRequest regRequest = new RegistrationRequest(
                    req.username(),
                    req.email(),
                    "Test@123", // Default password for seeds
                    req.firstName(),
                    req.lastName(),
                    LocalDate.of(1990, 1, 1),
                    null,
                    gender,
                    country
            );

            var userDto = authService.register(regRequest);

            if (req.isAdmin()) {
                userService.promoteUserToAdmin(userDto.id());
                log.info("✅ Created & Promoted Admin: {}", req.username());
            } else {
                log.info("✅ Created Regular User: {}", req.username());
            }

        } catch (Exception e) {
            log.error("❌ Failed to seed user {}: {}", req.username(), e.getMessage());
        }
    }

    private void seedKeycloakStructure() {
        setKeycloakRealmRoles();
        setKeycloakGroups();
    }

    private void setKeycloakRealmRoles() {
        Map<String, String> rolesToCreate = Map.of(
                RoleType.ROLE_USER.name(), "Standard platform user",
                RoleType.ROLE_ADMIN.name(), "Administrator with elevated privileges"
        );

        keycloakService.createRolesInKeyCloak(rolesToCreate);
    }

    private void setKeycloakGroups() {
        Map<String, String> map = Map.ofEntries(
                Map.entry("GIGTASKER_USERS", RoleType.ROLE_USER.name()),
                Map.entry("GIGTASKER_ADMIN_USERS", RoleType.ROLE_ADMIN.name())
        );
        keycloakService.createGroupsInKeyCloak(map);
    }

}
