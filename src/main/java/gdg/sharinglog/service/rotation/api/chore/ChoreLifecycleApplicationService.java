package gdg.sharinglog.service.rotation.api.chore;

import java.util.Map;

import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.repository.rotation.ChoreRepository;
import gdg.sharinglog.service.rotation.access.RotationActorAccessService;
import gdg.sharinglog.web.rotation.error.RotationConflictException;
import gdg.sharinglog.web.rotation.error.RotationNotFoundException;
import gdg.sharinglog.web.rotation.error.RotationProblemCode;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChoreLifecycleApplicationService {

    private final RotationActorAccessService accessService;
    private final ChoreRepository choreRepository;

    public ChoreLifecycleApplicationService(
            RotationActorAccessService accessService,
            ChoreRepository choreRepository
    ) {
        this.accessService = accessService;
        this.choreRepository = choreRepository;
    }

    @Transactional
    public long deactivate(
            String groupPublicId,
            String chorePublicId,
            String registrationId,
            OAuth2User principal,
            long expectedVersion
    ) {
        accessService.requireOwnerForUpdate(groupPublicId, registrationId, principal);
        Chore chore = choreRepository
                .findByPublicIdAndGroupPublicIdForUpdate(chorePublicId, groupPublicId)
                .orElseThrow(() -> new RotationNotFoundException(
                        "The requested chore was not found."
                ));
        requireVersion(chore, expectedVersion);
        if (chore.isActive()) {
            chore.deactivate();
            choreRepository.saveAndFlush(chore);
        }
        return chore.getVersion();
    }

    private void requireVersion(Chore chore, long expectedVersion) {
        if (chore.getVersion() != expectedVersion) {
            throw new RotationConflictException(
                    RotationProblemCode.VERSION_CONFLICT,
                    "The chore changed. Reload it and try again.",
                    Map.of(
                            "resourceId", chore.getPublicId(),
                            "expectedVersion", expectedVersion,
                            "currentVersion", chore.getVersion()
                    )
            );
        }
    }
}
