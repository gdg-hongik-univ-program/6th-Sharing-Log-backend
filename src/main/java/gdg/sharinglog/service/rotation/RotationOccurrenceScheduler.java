package gdg.sharinglog.service.rotation;

import java.time.Instant;

import gdg.sharinglog.repository.rotation.ChoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RotationOccurrenceScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(RotationOccurrenceScheduler.class);

    private final ChoreRepository choreRepository;
    private final OccurrenceGenerationService generationService;

    public RotationOccurrenceScheduler(
            ChoreRepository choreRepository,
            OccurrenceGenerationService generationService
    ) {
        this.choreRepository = choreRepository;
        this.generationService = generationService;
    }

    @Scheduled(
            cron = "${sharing-log.rotation.scheduler.cron:0 5 * * * *}",
            zone = "UTC"
    )
    public void ensureCurrentOccurrences() {
        Instant referenceTime = Instant.now();
        for (var chore : choreRepository.findAllActiveForOccurrenceGeneration()) {
            try {
                generationService.ensureCurrentOccurrence(chore.getId(), referenceTime);
            } catch (RuntimeException exception) {
                log.error(
                        "Failed to generate the current occurrence for chore {}",
                        chore.getPublicId(),
                        exception
                );
            }
        }
    }
}
