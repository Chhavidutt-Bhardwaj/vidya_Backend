package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.timetable.dto.CreateRoomRequest;
import com.vidya.tenantdata.timetable.dto.RoomResponse;
import com.vidya.tenantdata.timetable.dto.UpdateRoomRequest;
import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;
import com.vidya.tenantdata.timetable.mapper.RoomMapper;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public RoomResponse createRoom(CreateRoomRequest request) {
        log.info("Creating room with name: [{}]", request.name());

        if (roomRepository.existsByNameIgnoreCaseAndNotDeleted(request.name().trim(), null)) {
            throw new DuplicateResourceException("Room", "name", request.name().trim());
        }

        String roomCode = CodeGenerator.roomCode();
        while (roomRepository.existsByRoomCode(roomCode)) {
            roomCode = CodeGenerator.roomCode();
        }

        Room room = new Room(
                roomCode,
                request.name(),
                request.roomType(),
                request.capacity(),
                request.building(),
                request.floor()
        );

        Room saved = roomRepository.save(room);
        log.info("Room created successfully: [{}]", saved.getRoomCode());
        return roomMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public RoomResponse updateRoom(UUID id, UpdateRoomRequest request) {
        log.info("Updating room: [{}]", id);

        Room room = roomRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", id));

        if (roomRepository.existsByNameIgnoreCaseAndNotDeleted(request.name().trim(), id)) {
            throw new DuplicateResourceException("Room", "name", request.name().trim());
        }

        room.setName(request.name().trim());
        room.setRoomType(request.roomType());
        room.setCapacity(request.capacity());
        room.setBuilding(request.building() != null ? request.building().trim() : null);
        room.setFloor(request.floor() != null ? request.floor().trim() : null);
        room.updateStatus(request.status());

        Room updated = roomRepository.save(room);
        log.info("Room updated successfully: [{}]", updated.getRoomCode());
        return roomMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public RoomResponse getRoomById(UUID id) {
        Room room = roomRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", id));
        return roomMapper.toResponse(room);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public RoomResponse getRoomByCode(String roomCode) {
        Room room = roomRepository.findByRoomCodeAndNotDeleted(roomCode)
                .orElseThrow(() -> new ResourceNotFoundException("Room", roomCode));
        return roomMapper.toResponse(room);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<RoomResponse> searchRooms(String search, RoomType roomType, RoomStatus status, Pageable pageable) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        return roomRepository.searchRooms(roomType, status, query, pageable)
                .map(roomMapper::toResponse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteRoom(UUID id) {
        log.info("Deleting room: [{}]", id);
        Room room = roomRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", id));
        room.softDelete();
        roomRepository.save(room);
        log.info("Room deleted successfully: [{}]", id);
    }
}
