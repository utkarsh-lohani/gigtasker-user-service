package com.gigtasker.userservice.seed;

import com.gigtasker.userservice.dto.RegistrationRequest;
import com.gigtasker.userservice.dto.RestCountry;
import com.gigtasker.userservice.entity.Country;
import com.gigtasker.userservice.entity.Gender;
import com.gigtasker.userservice.entity.Region;
import com.gigtasker.userservice.entity.SubRegion;
import com.gigtasker.userservice.enums.GenderType;
import com.gigtasker.userservice.enums.RoleType;
import com.gigtasker.userservice.repository.CountryRepository;
import com.gigtasker.userservice.repository.GenderRepository;
import com.gigtasker.userservice.repository.RegionRepository;
import com.gigtasker.userservice.repository.SubRegionRepository;
import com.gigtasker.userservice.service.AuthService;
import com.gigtasker.userservice.service.RestCountriesService;
import com.gigtasker.userservice.service.UserService;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
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
    private final Keycloak keycloakBot;

    @Value("${keycloak.bot.realm}")
    private String realmName;

    @Value("${app.seeding.enabled:true}")
    private boolean seedingEnabled;

    @Value("${app.seeding.create-admin:true}")
    private boolean createAdmin;

    private static final String UNKNOWN = "Unknown";

    @Override
    public void run(String... args) throws Exception {
        if (!seedingEnabled) {
            log.info("Seeding is disabled in config.");
            return;
        }
        seedKeycloakStructure();
        seedCountries();
        seedAdminUser();
    }

    private void seedCountries() {
        if (countryRepository.count() > 0) {
            log.info("Countries already seeded");
            return;
        }

        List<RestCountry> apiCountries = restCountriesService.fetchCountries();

        Map<String, Region> regionMap = new HashMap<>();
        Map<String, SubRegion> subRegionMap = new HashMap<>();

        List<Country> countriesToSave = new ArrayList<>();

        for (RestCountry apiCountry : apiCountries) {
            String regionName = apiCountry.getRegion() != null ? apiCountry.getRegion() : UNKNOWN;
            String subRegionName = apiCountry.getSubregion() != null ? apiCountry.getSubregion() : UNKNOWN;

            regionMap.putIfAbsent(regionName,
                    Region.builder().name(regionName).build()
            );

            Region region = regionMap.get(regionName);

            String key = regionName + "___" + subRegionName;

            subRegionMap.putIfAbsent(key,
                    SubRegion.builder()
                            .name(subRegionName)
                            .region(region) // still needed
                            .build()
            );
        }

        List<Region> savedRegions = regionRepository.saveAll(regionMap.values());
        Map<String, Region> regionLookup = savedRegions.stream()
                .collect(Collectors.toMap(Region::getName, r -> r));

        List<SubRegion> subRegions = new ArrayList<>();
        for (SubRegion s : subRegionMap.values()) {
            Region savedRegion = regionLookup.get(s.getRegion().getName());
            s.setRegion(savedRegion);
            subRegions.add(s);
        }

        subRegionRepository.saveAll(subRegions);

        for (RestCountry apiCountry : apiCountries) {

            String regionName = apiCountry.getRegion() != null ? apiCountry.getRegion() : UNKNOWN;
            Region region = regionLookup.get(regionName);

            Country country = Country.builder()
                    .name(apiCountry.getName().getCommon())
                    .isoCode(apiCountry.getCca2())
                    .phoneCode(extractPhoneCode(apiCountry))
                    .currencyCode(extractCurrencyCode(apiCountry))
                    .region(region)    // ONLY REGION
                    .build();

            countriesToSave.add(country);
        }

        countryRepository.saveAll(countriesToSave);
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

    private void seedAdminUser() {
        // Check if admin exists
        if (!createAdmin || userService.getUserByEmail("admin-gigtasker@yopmail.com") != null) {
            log.info("Default Admin User Already Exists");
            return;
        }

        log.info("Creating Default Admin User...");

        try {
            Gender gender = genderRepository.findByName(GenderType.MAN)
                    .orElseThrow(() -> new RuntimeException("Gender 'MAN' not found in DB! Check Liquibase logs."));

            Country country = countryRepository.findByIsoCode("US")
                    .orElseThrow(() -> new RuntimeException("Country 'US' not found! RestCountries API might have failed."));

            RegistrationRequest adminReq = new RegistrationRequest(
                    "admin-gigtasker",
                    "admin-gigtasker@yopmail.com",
                    "Test@123",
                    "Super",
                    "Admin",
                    LocalDate.of(1990, 1, 1),
                    "System",
                    gender,
                    country
            );

            // ... rest of logic
            var userDto = authService.register(adminReq);
            userService.promoteUserToAdmin(userDto.getId());

            log.info("✅ Default Admin Created & Promoted.");

        } catch (Exception e) {
            log.error("❌ Failed to seed Admin: ", e);
            // Don't throw exception here, or it kills the whole app startup.
            // Just log it so the app can still run.
        }
    }

    private void seedKeycloakStructure() {
        setKeycloakRealmRoles();
        setKeycloakGroups();
    }

    private void setKeycloakRealmRoles() {
        Map<String, String> rolesToCreate = Map.of(
                "ROLE_USER", "Standard platform user",
                "ROLE_ADMIN", "Administrator with elevated privileges"
        );

        RolesResource rolesResource = keycloakBot.realm(realmName).roles();

        Set<String> existingRoles;
        try {
            existingRoles = getRealmRolesFromKeycloak();
        } catch (Exception e) {
            log.error("❌ Failed to fetch existing roles. Check 'user-service-bot' permissions!", e);
            return;
        }

        rolesToCreate.forEach((roleName, roleDescription) -> {
            if (existingRoles.contains(roleName)) {
                log.info("Role {} already exists", roleName);
                return;
            }

            log.info("Creating Role: {}", roleName);
            RoleRepresentation role = new RoleRepresentation();
            role.setName(roleName);
            role.setDescription(roleDescription);

            try {
                rolesResource.create(role);
                log.info("✅ Created realm role: {}", roleName);
            } catch (Exception ex) {
                log.error("❌ Failed to create realm role {}: {}", roleName, ex.getMessage());
            }
        });
    }

    private void setKeycloakGroups() {
        Map<String, String> map = Map.ofEntries(
                Map.entry("GIGTASKER_USERS", RoleType.ROLE_USER.name()),
                Map.entry("GIGTASKER_ADMIN_USERS", RoleType.ROLE_ADMIN.name())
        );

        map.forEach((groupName, groupRole) -> {
            try {
                List<GroupRepresentation> existing = keycloakBot.realm(realmName).groups().groups(groupName, 0, 1);

                if (existing.isEmpty()) {
                    log.info("Creating Group: {}", groupName);

                    GroupRepresentation group = new GroupRepresentation();
                    group.setName(groupName);
                    // Note: Setting setRealmRoles here is ignored by Keycloak API during creation

                    try (Response response = keycloakBot.realm(realmName).groups().add(group)) {
                        if (response.getStatus() == 201) {
                            // 1. Get the ID of the new group
                            String groupId = CreatedResponseUtil.getCreatedId(response);

                            // 2. Find the Role we want to assign
                            RoleRepresentation role = keycloakBot.realm(realmName)
                                    .roles()
                                    .get(groupRole)
                                    .toRepresentation();

                            // 3. Explicitly assign role to the group
                            keycloakBot.realm(realmName)
                                    .groups()
                                    .group(groupId)
                                    .roles()
                                    .realmLevel()
                                    .add(Collections.singletonList(role));

                            log.info("✅ Created Group {} and assigned role {}", groupName, groupRole);
                        } else {
                            log.warn("Keycloak returned error {} while creating group {}", response.getStatus(), groupName);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to seed group {} with Role {} - {}", groupName, groupRole, e.getMessage());
            }
        });
    }

    private Set<String> getRealmRolesFromKeycloak() {
        return keycloakBot.realm(realmName).roles().list().stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toSet());
    }

}
