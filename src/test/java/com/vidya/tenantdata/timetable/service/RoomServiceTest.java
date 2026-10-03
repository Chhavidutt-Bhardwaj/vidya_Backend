package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.timetable.dto.CreateRoomRequest;
import com.vidya.tenantdata.timetable.dto.RoomResponse;
import com.vidya.tenantdata.timetable.dto.UpdateRoomRequest;
import com.vidya.tenantdata.timetable.dto.UpdateRoomStatusRequest;
import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;
import com.vidya.tenantdata.timetable.mapper.RoomMapper;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomService — Unit Tests")
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private TimetableEntryRepository timetableEntryRepository;

    @Mock
    private RoomMapper roomMapper;

    @InjectMocks
    private RoomServiceImpl roomService;

    private UUID roomId;
    private Room activeRoom;
    private RoomResponse roomResponse;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        activeRoom = new Room(
                "RM-2026-0001",
                "Science Lab 1",
                RoomType.SCIENCE_LAB,
                40,
                "Science Block",
                "2nd Floor"
        );
        activeRoom.setId(roomId);

        roomResponse = new RoomResponse(
                roomId,
                "RM-2026-0001",
                "Science Lab 1",
                RoomType.SCIENCE_LAB,
                40,
                "Science Block",
                "2nd Floor",
                RoomStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                0L
        );
    }

    @Test
    @DisplayName("createRoom — Success")
    void createRoom_success() {
        CreateRoomRequest request = new CreateRoomRequest("Science Lab 1", RoomType.SCIENCE_LAB, 40, "Science Block", "2nd Floor");

        when(roomRepository.existsByNameIgnoreCaseAndNotDeleted("Science Lab 1", null)).thenReturn(false);
        when(roomRepository.existsByRoomCode(any())).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenReturn(activeRoom);
        when(roomMapper.toResponse(activeRoom)).thenReturn(roomResponse);

        RoomResponse result = roomService.createRoom(request);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(roomId);
        assertThat(result.name()).isEqualTo("Science Lab 1");
        assertThat(result.roomCode()).isEqualTo("RM-2026-0001");
        verify(roomRepository).save(any(Room.class));
    }

    @Test
    @DisplayName("createRoom — Duplicate name throws DuplicateResourceException")
    void createRoom_duplicateName_throwsDuplicateResourceException() {
        CreateRoomRequest request = new CreateRoomRequest("Science Lab 1", RoomType.SCIENCE_LAB, 40, "Science Block", "2nd Floor");

        when(roomRepository.existsByNameIgnoreCaseAndNotDeleted("Science Lab 1", null)).thenReturn(true);

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Science Lab 1");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRoom — DataIntegrityViolationException on save maps to DuplicateResourceException")
    void createRoom_dbConstraintViolation_throwsDuplicateResourceException() {
        CreateRoomRequest request = new CreateRoomRequest("Science Lab 1", RoomType.SCIENCE_LAB, 40, "Science Block", "2nd Floor");

        when(roomRepository.existsByNameIgnoreCaseAndNotDeleted("Science Lab 1", null)).thenReturn(false);
        when(roomRepository.existsByRoomCode(any())).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenThrow(new DataIntegrityViolationException("unique constraint idx_room_name_active"));

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("getRoomById — Success")
    void getRoomById_success() {
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(activeRoom));
        when(roomMapper.toResponse(activeRoom)).thenReturn(roomResponse);

        RoomResponse result = roomService.getRoomById(roomId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(roomId);
    }

    @Test
    @DisplayName("getRoomById — Not Found throws ResourceNotFoundException")
    void getRoomById_notFound_throwsResourceNotFoundException() {
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoomById(roomId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getRoomByCode — Success")
    void getRoomByCode_success() {
        when(roomRepository.findByRoomCodeAndNotDeleted("RM-2026-0001")).thenReturn(Optional.of(activeRoom));
        when(roomMapper.toResponse(activeRoom)).thenReturn(roomResponse);

        RoomResponse result = roomService.getRoomByCode("RM-2026-0001");

        assertThat(result).isNotNull();
        assertThat(result.roomCode()).isEqualTo("RM-2026-0001");
    }

    @Test
    @DisplayName("searchRooms — Success with pagination and filters")
    void searchRooms_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Room> page = new PageImpl<>(List.of(activeRoom), pageable, 1);

        when(roomRepository.searchRooms(RoomType.SCIENCE_LAB, RoomStatus.ACTIVE, "Science Block", "Science", pageable))
                .thenReturn(page);
        when(roomMapper.toResponse(activeRoom)).thenReturn(roomResponse);

        Page<RoomResponse> result = roomService.searchRooms("Science", RoomType.SCIENCE_LAB, RoomStatus.ACTIVE, "Science Block", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Science Lab 1");
    }

    @Test
    @DisplayName("updateRoom — Success")
    void updateRoom_success() {
        UpdateRoomRequest request = new UpdateRoomRequest("Updated Lab 1", RoomType.SCIENCE_LAB, 45, "Main Block", "1st Floor", RoomStatus.ACTIVE);

        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(activeRoom));
        when(roomRepository.existsByNameIgnoreCaseAndNotDeleted("Updated Lab 1", roomId)).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenReturn(activeRoom);
        when(roomMapper.toResponse(activeRoom)).thenReturn(roomResponse);

        RoomResponse result = roomService.updateRoom(roomId, request);

        assertThat(result).isNotNull();
        verify(roomRepository).save(activeRoom);
    }

    @Test
    @DisplayName("updateRoom — Duplicate name throws DuplicateResourceException")
    void updateRoom_duplicateName_throwsDuplicateResourceException() {
        UpdateRoomRequest request = new UpdateRoomRequest("Existing Lab", RoomType.SCIENCE_LAB, 45, "Main Block", "1st Floor", RoomStatus.ACTIVE);

        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(activeRoom));
        when(roomRepository.existsByNameIgnoreCaseAndNotDeleted("Existing Lab", roomId)).thenReturn(true);

        assertThatThrownBy(() -> roomService.updateRoom(roomId, request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRoom — Setting DELETED via updateRoom throws InvalidStateTransitionException")
    void updateRoom_deletedTarget_throwsInvalidStateTransitionException() {
        UpdateRoomRequest request = new UpdateRoomRequest("Science Lab 1", RoomType.SCIENCE_LAB, 45, "Science Block", "2nd Floor", RoomStatus.DELETED);

        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(activeRoom));

        assertThatThrownBy(() -> roomService.updateRoom(roomId, request))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("DELETED");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRoom — Deactivation blocked when room is in active timetable")
    void updateRoom_inUse_deactivationBlocked_throwsBusinessException() {
        UpdateRoomRequest request = new UpdateRoomRequest("Science Lab 1", RoomType.SCIENCE_LAB, 45, "Science Block", "2nd Floor", RoomStatus.INACTIVE);

        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(activeRoom));
        when(timetableEntryRepository.isRoomUsedInActiveTimetables(roomId)).thenReturn(true);

        assertThatThrownBy(() -> roomService.updateRoom(roomId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot change status of room");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRoomStatus — Success")
    void updateRoomStatus_success() {
        UpdateRoomStatusRequest request = new UpdateRoomStatusRequest(RoomStatus.MAINTENANCE);

        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(activeRoom));
        when(timetableEntryRepository.isRoomUsedInActiveTimetables(roomId)).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenReturn(activeRoom);
        when(roomMapper.toResponse(activeRoom)).thenReturn(roomResponse);

        RoomResponse result = roomService.updateRoomStatus(roomId, request);

        assertThat(result).isNotNull();
        assertThat(activeRoom.getStatus()).isEqualTo(RoomStatus.MAINTENANCE);
        verify(roomRepository).save(activeRoom);
    }

    @Test
    @DisplayName("updateRoomStatus — Setting DELETED via status endpoint throws InvalidStateTransitionException")
    void updateRoomStatus_deletedTarget_throwsInvalidStateTransitionException() {
        UpdateRoomStatusRequest request = new UpdateRoomStatusRequest(RoomStatus.DELETED);

        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(activeRoom));

        assertThatThrownBy(() -> roomService.updateRoomStatus(roomId, request))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("DELETED");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateRoomStatus — Deactivation blocked when room in use in active timetable")
    void updateRoomStatus_inUse_blocked() {
        UpdateRoomStatusRequest request = new UpdateRoomStatusRequest(RoomStatus.INACTIVE);

        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(activeRoom));
        when(timetableEntryRepository.isRoomUsedInActiveTimetables(roomId)).thenReturn(true);

        assertThatThrownBy(() -> roomService.updateRoomStatus(roomId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot change status of room");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteRoom — Success soft deletes room")
    void deleteRoom_success() {
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(activeRoom));
        when(timetableEntryRepository.isRoomUsedInActiveTimetables(roomId)).thenReturn(false);

        roomService.deleteRoom(roomId);

        assertThat(activeRoom.isDeleted()).isTrue();
        assertThat(activeRoom.getStatus()).isEqualTo(RoomStatus.DELETED);
        verify(roomRepository).save(activeRoom);
    }

    @Test
    @DisplayName("deleteRoom — In use in active timetable throws BusinessException")
    void deleteRoom_inUse_throwsBusinessException() {
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(activeRoom));
        when(timetableEntryRepository.isRoomUsedInActiveTimetables(roomId)).thenReturn(true);

        assertThatThrownBy(() -> roomService.deleteRoom(roomId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot delete room");

        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteRoom — Not found throws ResourceNotFoundException")
    void deleteRoom_notFound_throwsResourceNotFoundException() {
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.deleteRoom(roomId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Room auditing — createdBy and updatedBy set on create and updatedBy changes on update")
    void roomAuditing_createdByAndUpdatedBy_populated() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        var principal1 = new com.vidya.security.user.PlatformUserDetails(
                user1, "admin@school.com", "hash", "SCHOOL_ADMIN", java.util.Collections.emptyList()
        );
        var auth1 = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal1, null, principal1.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth1);

        try {
            CreateRoomRequest createReq = new CreateRoomRequest("Audit Lab", RoomType.CLASSROOM, 40, "Building A", "Floor 1");
            when(roomRepository.existsByNameIgnoreCaseAndNotDeleted("Audit Lab", null)).thenReturn(false);
            when(roomRepository.existsByRoomCode(any())).thenReturn(false);
            when(roomRepository.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

            roomService.createRoom(createReq);

            org.mockito.ArgumentCaptor<Room> captor = org.mockito.ArgumentCaptor.forClass(Room.class);
            verify(roomRepository).save(captor.capture());
            Room created = captor.getValue();
            assertThat(created.getCreatedBy()).isEqualTo(user1);
            assertThat(created.getUpdatedBy()).isEqualTo(user1);

            // Now update with user2
            var principal2 = new com.vidya.security.user.PlatformUserDetails(
                    user2, "admin2@school.com", "hash", "SCHOOL_ADMIN", java.util.Collections.emptyList()
            );
            var auth2 = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal2, null, principal2.getAuthorities());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth2);

            UpdateRoomRequest updateReq = new UpdateRoomRequest("Audit Lab Renamed", RoomType.CLASSROOM, 45, "Building A", "Floor 1", RoomStatus.ACTIVE);
            when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(created));
            when(roomRepository.existsByNameIgnoreCaseAndNotDeleted("Audit Lab Renamed", roomId)).thenReturn(false);

            roomService.updateRoom(roomId, updateReq);
            assertThat(created.getCreatedBy()).isEqualTo(user1);
            assertThat(created.getUpdatedBy()).isEqualTo(user2);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
