package gdg.sharinglog.service.rotation.occurrence;

import java.time.Instant;

import gdg.sharinglog.repository.rotation.ChoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RotationOccurrenceScheduler {

    private final ChoreRepository choreRepository;
    private final OccurrenceGenerationService generationService;

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
