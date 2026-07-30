package gdg.sharinglog.web.rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import gdg.sharinglog.domain.GroupMember;
import gdg.sharinglog.domain.SharingGroup;
import gdg.sharinglog.domain.User;
import gdg.sharinglog.domain.rotation.Chore;
import gdg.sharinglog.domain.rotation.ChoreOccurrence;
import gdg.sharinglog.domain.rotation.OccurrenceStatus;
import gdg.sharinglog.repository.rotation.ChoreAssignmentAttemptRepository;
import gdg.sharinglog.repository.rotation.SubstituteRequestRepository;
import gdg.sharinglog.service.rotation.access.RotationActor;
import gdg.sharinglog.web.rotation.dto.OccurrenceSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotationViewMapperTest {

    @Mock
    ChoreAssignmentAttemptRepository assignmentRepository;

    @Mock
    SubstituteRequestRepository substituteRequestRepository;

    @InjectMocks
    RotationViewMapper mapper;

    @Test
    void currentOccurrenceUsesCurrentChoreNameAndOnlyCurrentWireframeActions() {
        ChoreOccurrence occurrence = org.mockito.Mockito.mock(ChoreOccurrence.class);
        Chore chore = org.mockito.Mockito.mock(Chore.class);
        SharingGroup group = org.mockito.Mockito.mock(SharingGroup.class);
        GroupMember membership = org.mockito.Mockito.mock(GroupMember.class);
        User user = org.mockito.Mockito.mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);

        when(occurrence.getId()).thenReturn(20L);
        when(occurrence.getChore()).thenReturn(chore);
        when(chore.getName()).thenReturn("변경된 업무명");
        when(occurrence.getStatus()).thenReturn(OccurrenceStatus.ASSIGNED);
        when(occurrence.currentAssignee()).thenReturn(Optional.of(membership));
        when(membership.getId()).thenReturn(10L);
        when(membership.getUser()).thenReturn(user);
        when(substituteRequestRepository.findByOccurrence_IdAndActiveMarker(20L, 1))
                .thenReturn(Optional.empty());

        OccurrenceSummaryResponse response = mapper.occurrence(occurrence, actor);

        assertEquals("변경된 업무명", response.choreName());
        assertEquals(
                List.of(
                        OccurrenceSummaryResponse.AvailableAction.COMPLETE,
                        OccurrenceSummaryResponse.AvailableAction.REQUEST_SUBSTITUTE
                ),
                response.availableActions()
        );
    }

    @Test
    void completedHistoryKeepsOccurrenceNameSnapshot() {
        ChoreOccurrence occurrence = org.mockito.Mockito.mock(ChoreOccurrence.class);
        Chore chore = org.mockito.Mockito.mock(Chore.class);
        SharingGroup group = org.mockito.Mockito.mock(SharingGroup.class);
        GroupMember membership = org.mockito.Mockito.mock(GroupMember.class);
        User user = org.mockito.Mockito.mock(User.class);
        RotationActor actor = new RotationActor(group, membership, user);

        when(occurrence.getChore()).thenReturn(chore);
        when(occurrence.getChoreNameSnapshot()).thenReturn("완료 당시 업무명");
        when(occurrence.getStatus()).thenReturn(OccurrenceStatus.ASSIGNED);
        when(occurrence.currentAssignee()).thenReturn(Optional.empty());

        OccurrenceSummaryResponse response = mapper.completedOccurrence(occurrence, actor);

        assertEquals("완료 당시 업무명", response.choreName());
    }
}
