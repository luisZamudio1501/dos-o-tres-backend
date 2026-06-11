package com.dosotres.seed;

import com.dosotres.group.Group;
import com.dosotres.group.GroupMember;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.group.GroupRepository;
import com.dosotres.group.GroupRole;
import com.dosotres.prayer.PrayerCommitment;
import com.dosotres.prayer.PrayerCommitmentRepository;
import com.dosotres.prayer.PrayerRequest;
import com.dosotres.prayer.PrayerRequestRepository;
import com.dosotres.prayer.PrayerRequestStatus;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PrayerRequestRepository prayerRequestRepository;
    private final PrayerCommitmentRepository commitmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DataSeeder(UserRepository userRepository,
                      GroupRepository groupRepository,
                      GroupMemberRepository groupMemberRepository,
                      PrayerRequestRepository prayerRequestRepository,
                      PrayerCommitmentRepository commitmentRepository,
                      PasswordEncoder passwordEncoder,
                      Clock clock) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.prayerRequestRepository = prayerRequestRepository;
        this.commitmentRepository = commitmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail("demo@dosotres.app").isPresent()) {
            log.info("Seed data already exists, skipping");
            return;
        }

        log.info("Seeding demo data...");

        User demo = new User();
        demo.setEmail("demo@dosotres.app");
        demo.setDisplayName("Luis Demo");
        demo.setPasswordHash(passwordEncoder.encode("demo1234"));
        demo = userRepository.save(demo);

        User demo2 = new User();
        demo2.setEmail("demo2@dosotres.app");
        demo2.setDisplayName("Ana Demo");
        demo2.setPasswordHash(passwordEncoder.encode("demo1234"));
        demo2 = userRepository.save(demo2);

        Group group = new Group();
        group.setName("Familia Zamudio");
        group.setDescription("Grupo de oración familiar");
        group.setInviteCode(UUID.randomUUID().toString());
        group.setCreatedBy(demo);
        group = groupRepository.save(group);

        GroupMember adminMember = new GroupMember();
        adminMember.setGroup(group);
        adminMember.setUser(demo);
        adminMember.setRole(GroupRole.ADMIN);
        groupMemberRepository.save(adminMember);

        GroupMember regularMember = new GroupMember();
        regularMember.setGroup(group);
        regularMember.setUser(demo2);
        regularMember.setRole(GroupRole.MEMBER);
        groupMemberRepository.save(regularMember);

        PrayerRequest pr1 = new PrayerRequest();
        pr1.setGroup(group);
        pr1.setAuthor(demo);
        pr1.setTitle("Salud para mamá");
        pr1.setDescription("Está pasando por un tratamiento médico. Oremos por sanidad completa.");
        pr1.setStatus(PrayerRequestStatus.ACTIVE);
        pr1 = prayerRequestRepository.save(pr1);

        PrayerRequest pr2 = new PrayerRequest();
        pr2.setGroup(group);
        pr2.setAuthor(demo2);
        pr2.setTitle("Trabajo nuevo para Juan");
        pr2.setDescription("Lleva 3 meses buscando empleo. Pedimos puertas abiertas.");
        pr2.setStatus(PrayerRequestStatus.ACTIVE);
        pr2 = prayerRequestRepository.save(pr2);

        PrayerRequest pr3 = new PrayerRequest();
        pr3.setGroup(group);
        pr3.setAuthor(demo);
        pr3.setTitle("Examen de la facultad");
        pr3.setDescription("Tenía un final muy difícil. ¡Dios respondió y aprobó!");
        pr3.setStatus(PrayerRequestStatus.ANSWERED);
        pr3.setAnsweredAt(Instant.now(clock));
        pr3 = prayerRequestRepository.save(pr3);

        LocalDate today = LocalDate.now(clock);

        PrayerCommitment c1 = new PrayerCommitment();
        c1.setPrayerRequest(pr1);
        c1.setUser(demo);
        c1.setCommittedDate(today);
        c1.setFulfilled(true);
        c1.setFulfilledAt(Instant.now(clock));
        commitmentRepository.save(c1);

        PrayerCommitment c2 = new PrayerCommitment();
        c2.setPrayerRequest(pr1);
        c2.setUser(demo2);
        c2.setCommittedDate(today);
        c2.setFulfilled(false);
        commitmentRepository.save(c2);

        log.info("Seed data created: 2 users, 1 group, 3 prayer requests, 2 commitments");
    }
}
