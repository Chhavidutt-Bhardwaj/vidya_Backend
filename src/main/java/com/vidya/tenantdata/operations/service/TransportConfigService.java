package com.vidya.tenantdata.operations.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.config.service.TeachingScope;
import com.vidya.tenantdata.operations.dto.OperationsDtos.RouteRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.RouteResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.StopInput;
import com.vidya.tenantdata.operations.dto.OperationsDtos.StopResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.VehicleRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.VehicleResponse;
import com.vidya.tenantdata.operations.entity.TransportRoute;
import com.vidya.tenantdata.operations.entity.TransportStop;
import com.vidya.tenantdata.operations.entity.TransportVehicle;
import com.vidya.tenantdata.operations.enums.ConfigStatus;
import com.vidya.tenantdata.operations.enums.VehicleStatus;
import com.vidya.tenantdata.operations.enums.VehicleType;
import com.vidya.tenantdata.operations.repository.TransportRouteRepository;
import com.vidya.tenantdata.operations.repository.TransportStopRepository;
import com.vidya.tenantdata.operations.repository.TransportVehicleRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Transport setup: vehicles with their crew and papers, and routes with their stops. Student allocation is not part of this service. */
@Service
@RequiredArgsConstructor
public class TransportConfigService {

    /** Papers running out within this many days are reported. */
    static final int PAPERS_WARNING_DAYS = 30;

    private final TransportVehicleRepository vehicleRepository;
    private final TransportRouteRepository routeRepository;
    private final TransportStopRepository stopRepository;
    private final StaffRepository staffRepository;
    private final TeachingScope names;

    // ── Vehicles ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<VehicleResponse> vehicles() {
        return respondVehicles(vehicleRepository.findByDeletedAtIsNullOrderByRegistrationNoAsc());
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public VehicleResponse saveVehicle(UUID id, VehicleRequest r) {
        TransportVehicle v = id == null ? new TransportVehicle()
                : vehicleRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("TransportVehicle", id));
        String registration = r.registrationNo().trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (r.driverStaffId() != null && r.driverStaffId().equals(r.attendantStaffId())) {
            throw new BusinessException("The driver and the attendant are the same person");
        }
        for (TransportVehicle other : vehicleRepository.findByDeletedAtIsNullOrderByRegistrationNoAsc()) {
            if (other.getId().equals(v.getId())) {
                continue;
            }
            if (plain(other.getRegistrationNo()).equals(plain(registration))) {
                throw new DuplicateResourceException("TransportVehicle", "registrationNo", registration);
            }
            if (r.driverStaffId() != null && r.driverStaffId().equals(other.getDriverStaffId())) {
                throw new BusinessException("DRIVER_TAKEN", "This driver is already the driver of " + other.getRegistrationNo());
            }
        }
        activeStaff(r.driverStaffId(), "driver");
        activeStaff(r.attendantStaffId(), "attendant");
        if (id == null) {
            v.setVehicleCode(CodeGenerator.vehicleCode());
        }
        v.setRegistrationNo(registration);
        v.setVehicleType(r.vehicleType() != null ? r.vehicleType() : VehicleType.BUS);
        v.setCapacity(r.capacity());
        v.setDriverStaffId(r.driverStaffId());
        v.setAttendantStaffId(r.attendantStaffId());
        v.setDriverName(FeeConfigService.blank(r.driverName()) ? null : r.driverName().trim());
        v.setDriverPhone(FeeConfigService.blank(r.driverPhone()) ? null : r.driverPhone().trim());
        v.setInsuranceValidTill(r.insuranceValidTill());
        v.setFitnessValidTill(r.fitnessValidTill());
        v.setPermitValidTill(r.permitValidTill());
        v.setPucValidTill(r.pucValidTill());
        v.setStatus(r.status() != null ? r.status() : VehicleStatus.ACTIVE);
        return respondVehicles(List.of(vehicleRepository.save(v))).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteVehicle(UUID id) {
        TransportVehicle v = vehicleRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("TransportVehicle", id));
        List<String> routes = routeRepository.findByDeletedAtIsNullOrderByNameAsc().stream().filter(r -> id.equals(r.getVehicleId()))
                .map(TransportRoute::getName).toList();
        if (!routes.isEmpty()) {
            throw new BusinessException("VEHICLE_IN_USE", v.getRegistrationNo() + " runs " + routes + "; give those routes another vehicle first");
        }
        v.setDeletedAt(Instant.now());
        v.setStatus(VehicleStatus.INACTIVE);
        vehicleRepository.save(v);
    }

    // ── Routes ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<RouteResponse> routes() {
        return respondRoutes(routeRepository.findByDeletedAtIsNullOrderByNameAsc());
    }

    /** Creates (no id) or replaces a route with its stops, in the order given. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public RouteResponse saveRoute(RouteRequest r) {
        TransportRoute route = r.id() == null ? new TransportRoute()
                : routeRepository.findByIdAndDeletedAtIsNull(r.id()).orElseThrow(() -> new ResourceNotFoundException("TransportRoute", r.id()));
        String name = r.name().trim();
        if (routeRepository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .anyMatch(o -> !o.getId().equals(route.getId()) && o.getName().equalsIgnoreCase(name))) {
            throw new DuplicateResourceException("TransportRoute", "name", name);
        }
        if (r.vehicleId() != null) {
            TransportVehicle v = vehicleRepository.findByIdAndDeletedAtIsNull(r.vehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("TransportVehicle", r.vehicleId()));
            if (v.getStatus() == VehicleStatus.INACTIVE) {
                throw new BusinessException(v.getRegistrationNo() + " is inactive and cannot be put on a route");
            }
        }
        List<StopInput> stops = r.stops() != null ? r.stops() : List.of();
        Set<String> seen = new HashSet<>();
        LocalTime previousPickup = null;
        for (StopInput s : stops) {
            if (!seen.add(s.name().trim().toLowerCase(Locale.ROOT))) {
                throw new BusinessException("Stop " + s.name().trim() + " is listed twice");
            }
            if (s.pickupTime() != null) {
                if (previousPickup != null && s.pickupTime().isBefore(previousPickup)) {
                    throw new BusinessException("Stops are in travel order: " + s.name().trim() + " is picked up at " + s.pickupTime()
                            + ", before the stop ahead of it (" + previousPickup + ")");
                }
                previousPickup = s.pickupTime();
            }
        }

        if (r.id() == null) {
            route.setRouteCode(CodeGenerator.routeCode());
        }
        route.setName(name);
        route.setVehicleId(r.vehicleId());
        route.setDescription(FeeConfigService.blank(r.description()) ? null : r.description().trim());
        route.setStatus(r.status() != null ? r.status() : ConfigStatus.ACTIVE);
        TransportRoute saved = routeRepository.save(route);

        Map<UUID, TransportStop> current = new HashMap<>();
        if (r.id() != null) {
            stopRepository.findByRouteIdOrderByStopNoAsc(saved.getId()).forEach(s -> current.put(s.getId(), s));
        }
        Set<UUID> kept = new LinkedHashSet<>();
        int no = 0;
        for (StopInput in : stops) {
            TransportStop stop;
            if (in.id() != null) {
                stop = current.get(in.id());
                if (stop == null || !kept.add(in.id())) {
                    throw new BusinessException("Stop " + in.id() + " does not belong to this route");
                }
            } else {
                stop = new TransportStop(saved.getId());
            }
            stop.setStopNo(++no);
            stop.setName(in.name().trim());
            stop.setPickupTime(in.pickupTime());
            stop.setDropTime(in.dropTime());
            stop.setDistanceKm(in.distanceKm());
            stop.setMonthlyFee(in.monthlyFee());
            stopRepository.save(stop);
        }
        List<TransportStop> removed = current.values().stream().filter(s -> !kept.contains(s.getId())).toList();
        if (!removed.isEmpty()) {
            stopRepository.deleteAll(removed);
        }
        return respondRoutes(List.of(saved)).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteRoute(UUID id) {
        TransportRoute route = routeRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("TransportRoute", id));
        route.setDeletedAt(Instant.now());
        route.setStatus(ConfigStatus.INACTIVE);
        routeRepository.save(route);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Papers that have run out or run out within {@link #PAPERS_WARNING_DAYS} days. */
    public static List<String> papers(TransportVehicle v, LocalDate today) {
        List<String> out = new ArrayList<>();
        paper(out, "Insurance", v.getInsuranceValidTill(), today);
        paper(out, "Fitness certificate", v.getFitnessValidTill(), today);
        paper(out, "Permit", v.getPermitValidTill(), today);
        paper(out, "Pollution certificate", v.getPucValidTill(), today);
        return out;
    }

    private static void paper(List<String> out, String label, LocalDate validTill, LocalDate today) {
        if (validTill == null) {
            return;
        }
        if (validTill.isBefore(today)) {
            out.add(label + " expired on " + validTill);
        } else if (!validTill.isAfter(today.plusDays(PAPERS_WARNING_DAYS))) {
            out.add(label + " expires on " + validTill);
        }
    }

    private void activeStaff(UUID staffId, String role) {
        if (staffId == null) {
            return;
        }
        Staff staff = staffRepository.findByIdAndNotDeleted(staffId).orElseThrow(() -> new ResourceNotFoundException("Staff", staffId));
        if (staff.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new BusinessException("The " + role + " is not an active staff member (" + staff.getEmploymentStatus() + ")");
        }
    }

    private static String plain(String registration) {
        return registration.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private List<VehicleResponse> respondVehicles(List<TransportVehicle> vehicles) {
        Set<UUID> staffIds = vehicles.stream().flatMap(v -> java.util.stream.Stream.of(v.getDriverStaffId(), v.getAttendantStaffId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> staffNames = staffIds.isEmpty() ? Map.of() : names.staffNames(staffIds);
        Map<UUID, List<String>> routes = routeRepository.findByDeletedAtIsNullOrderByNameAsc().stream().filter(r -> r.getVehicleId() != null)
                .collect(Collectors.groupingBy(TransportRoute::getVehicleId, Collectors.mapping(TransportRoute::getName, Collectors.toList())));
        LocalDate today = LocalDate.now();
        return vehicles.stream().map(v -> new VehicleResponse(v.getId(), v.getVehicleCode(), v.getRegistrationNo(), v.getVehicleType(), v.getCapacity(),
                v.getDriverStaffId(), v.getDriverStaffId() != null ? staffNames.getOrDefault(v.getDriverStaffId(), v.getDriverName()) : v.getDriverName(),
                v.getDriverPhone(), v.getAttendantStaffId(), v.getAttendantStaffId() != null ? staffNames.get(v.getAttendantStaffId()) : null,
                v.getInsuranceValidTill(), v.getFitnessValidTill(), v.getPermitValidTill(), v.getPucValidTill(), v.getStatus(), papers(v, today),
                routes.getOrDefault(v.getId(), List.of()))).toList();
    }

    private List<RouteResponse> respondRoutes(List<TransportRoute> routes) {
        if (routes.isEmpty()) {
            return List.of();
        }
        Map<UUID, TransportVehicle> vehicles = vehicleRepository.findByDeletedAtIsNullOrderByRegistrationNoAsc().stream()
                .collect(Collectors.toMap(TransportVehicle::getId, v -> v));
        Map<UUID, List<StopResponse>> stops = stopRepository.findByRouteIdInOrderByStopNoAsc(routes.stream().map(TransportRoute::getId).toList()).stream()
                .collect(Collectors.groupingBy(TransportStop::getRouteId, Collectors.mapping(s -> new StopResponse(s.getId(), s.getStopNo(), s.getName(),
                        s.getPickupTime(), s.getDropTime(), s.getDistanceKm(), s.getMonthlyFee()), Collectors.toList())));
        return routes.stream().map(r -> {
            TransportVehicle v = r.getVehicleId() == null ? null : vehicles.get(r.getVehicleId());
            return new RouteResponse(r.getId(), r.getRouteCode(), r.getName(), r.getVehicleId(), v != null ? v.getRegistrationNo() : null,
                    v != null ? v.getCapacity() : null, r.getDescription(), r.getStatus(), stops.getOrDefault(r.getId(), List.of()));
        }).toList();
    }
}
