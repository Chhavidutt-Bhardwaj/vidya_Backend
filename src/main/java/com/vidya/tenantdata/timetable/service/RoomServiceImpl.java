package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final TimetableEntryRepository timetableEntryRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public RoomResponse createRoom(CreateRoomRequest request) {
        log.info("Creating room with name: [{}]", request.name());

        String name = request.name().trim();
        if (roomRepository.existsByNameIgnoreCaseAndNotDeleted(name, null)) {
            throw new DuplicateResourceException("Room", "name", name);
        }

        String roomCode = CodeGenerator.roomCode();
        while (roomRepository.existsByRoomCode(roomCode)) {
            roomCode = CodeGenerator.roomCode();
        }

        Room room = new Room(
                roomCode,
                name,
                request.roomType(),
                request.capacity(),
                request.building(),
                request.floor()
        );
        room.setCreatedBy(com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null));
        room.setUpdatedBy(com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null));

        try {
            Room saved = roomRepository.save(room);
            log.info("Room created successfully: [{}]", saved.getRoomCode());
            return roomMapper.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Database unique constraint violation on room creation: {}", e.getMessage());
            throw new DuplicateResourceException("Room", "name", name);
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public RoomResponse updateRoom(UUID id, UpdateRoomRequest request) {
        log.info("Updating room: [{}]", id);

        Room room = roomRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", id));

        String name = request.name().trim();
        if (roomRepository.existsByNameIgnoreCaseAndNotDeleted(name, id)) {
            throw new DuplicateResourceException("Room", "name", name);
        }

        // Deactivating or soft deleting a room is blocked if it is in active use
        if (request.status() == RoomStatus.DELETED) {
            throw new InvalidStateTransitionException("Room", room.getStatus().name(), "DELETED (Use soft delete endpoint)");
        }
        if (request.status() != RoomStatus.ACTIVE && request.status() != room.getStatus()) {
            validateRoomNotInActiveTimetable(id, room.getRoomCode(), "change status of");
        }

        room.setName(name);
        room.setRoomType(request.roomType());
        room.setCapacity(request.capacity());
        room.setBuilding(request.building() != null ? request.building().trim() : null);
        room.setFloor(request.floor() != null ? request.floor().trim() : null);
        room.updateStatus(request.status());
        room.setUpdatedBy(com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null));

        try {
            Room updated = roomRepository.save(room);
            log.info("Room updated successfully: [{}]", updated.getRoomCode());
            return roomMapper.toResponse(updated);
        } catch (DataIntegrityViolationException e) {
            log.warn("Database unique constraint violation on room update: {}", e.getMessage());
            throw new DuplicateResourceException("Room", "name", name);
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public RoomResponse updateRoomStatus(UUID id, UpdateRoomStatusRequest request) {
        log.info("Updating status of room [{}] to [{}]", id, request.status());

        Room room = roomRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", id));

        if (request.status() == RoomStatus.DELETED) {
            throw new InvalidStateTransitionException("Room", room.getStatus().name(), "DELETED (Use soft delete endpoint)");
        }

        if (request.status() != RoomStatus.ACTIVE && request.status() != room.getStatus()) {
            validateRoomNotInActiveTimetable(id, room.getRoomCode(), "change status of");
        }

        room.updateStatus(request.status());
        room.setUpdatedBy(com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null));
        Room updated = roomRepository.save(room);
        log.info("Room status updated successfully: [{}] -> [{}]", updated.getRoomCode(), updated.getStatus());
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
    public Page<RoomResponse> searchRooms(String search, RoomType roomType, RoomStatus status, String building, Pageable pageable) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        String buildingQuery = (building != null && !building.isBlank()) ? building.trim() : null;
        return roomRepository.searchRooms(roomType, status, buildingQuery, query, pageable)
                .map(roomMapper::toResponse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteRoom(UUID id) {
        log.info("Deleting room: [{}]", id);
        Room room = roomRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room", id));

        validateRoomNotInActiveTimetable(id, room.getRoomCode(), "delete");

        room.softDelete();
        room.setUpdatedBy(com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null));
        roomRepository.save(room);
        log.info("Room deleted successfully: [{}]", id);
    }

    private void validateRoomNotInActiveTimetable(UUID roomId, String roomCode, String action) {
        if (timetableEntryRepository.isRoomUsedInActiveTimetables(roomId)) {
            throw new BusinessException(String.format(
                    "Cannot %s room [%s] because it is currently assigned to active timetable entries in non-archived timetables.",
                    action, roomCode
            ));
        }
    }
}
