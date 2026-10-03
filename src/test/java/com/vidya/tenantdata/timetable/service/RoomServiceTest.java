package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.timetable.dto.CreateRoomRequest;
import com.vidya.tenantdata.timetable.dto.RoomResponse;
import com.vidya.tenantdata.timetable.dto.UpdateRoomRequest;
import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;
import com.vidya.tenantdata.timetable.mapper.RoomMapper;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomService — Unit Tests")
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Spy
    private RoomMapper roomMapper = new RoomMapper();

    @InjectMocks
    private RoomServiceImpl roomService;

    private Room testRoom;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        testRoom = new Room("ROM-12345678", "Room 101", RoomType.CLASSROOM, 40, "Main Wing", "1st Floor");
        testRoom.setId(roomId);
    }

    @Test
    @DisplayName("Create room successfully")
    void createRoom_success() {
        CreateRoomRequest request = new CreateRoomRequest("Room 101", RoomType.CLASSROOM, 40, "Main Wing", "1st Floor");
        when(roomRepository.existsByNameIgnoreCaseAndNotDeleted("Room 101", null)).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(i -> {
            Room r = i.getArgument(0);
            r.setId(roomId);
            return r;
        });

        RoomResponse response = roomService.createRoom(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Room 101");
        assertThat(response.roomType()).isEqualTo(RoomType.CLASSROOM);
        assertThat(response.capacity()).isEqualTo(40);
        verify(roomRepository).save(any(Room.class));
    }

    @Test
    @DisplayName("Create room fails when name already exists")
    void createRoom_duplicateName_throwsException() {
        CreateRoomRequest request = new CreateRoomRequest("Room 101", RoomType.CLASSROOM, 40, "Main Wing", "1st Floor");
        when(roomRepository.existsByNameIgnoreCaseAndNotDeleted("Room 101", null)).thenReturn(true);

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Update room successfully")
    void updateRoom_success() {
        UpdateRoomRequest request = new UpdateRoomRequest("Room 101-Renovated", RoomType.LAB, 50, "Science Wing", "2nd Floor", RoomStatus.ACTIVE);
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));
        when(roomRepository.existsByNameIgnoreCaseAndNotDeleted("Room 101-Renovated", roomId)).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

        RoomResponse response = roomService.updateRoom(roomId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Room 101-Renovated");
        assertThat(response.roomType()).isEqualTo(RoomType.LAB);
        assertThat(response.capacity()).isEqualTo(50);
    }

    @Test
    @DisplayName("Get room by ID successfully")
    void getRoomById_success() {
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));

        RoomResponse response = roomService.getRoomById(roomId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(roomId);
        assertThat(response.name()).isEqualTo("Room 101");
    }

    @Test
    @DisplayName("Get room by ID not found throws ResourceNotFoundException")
    void getRoomById_notFound_throwsException() {
        UUID nonExistent = UUID.randomUUID();
        when(roomRepository.findByIdAndNotDeleted(nonExistent)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoomById(nonExistent))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Search rooms returns paginated response")
    void searchRooms_success() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(roomRepository.searchRooms(RoomType.CLASSROOM, RoomStatus.ACTIVE, "Room", pageRequest))
                .thenReturn(new PageImpl<>(List.of(testRoom), pageRequest, 1));

        Page<RoomResponse> result = roomService.searchRooms("Room", RoomType.CLASSROOM, RoomStatus.ACTIVE, pageRequest);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().name()).isEqualTo("Room 101");
    }

    @Test
    @DisplayName("Delete room soft-deletes the entity")
    void deleteRoom_success() {
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));
        when(roomRepository.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

        roomService.deleteRoom(roomId);

        assertThat(testRoom.getStatus()).isEqualTo(RoomStatus.DELETED);
        assertThat(testRoom.getDeletedAt()).isNotNull();
    }
}
