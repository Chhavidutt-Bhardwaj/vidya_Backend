package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.AreaGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** A graded non-scholastic area of a scheme (Work Education, Art, Discipline, ATL skills ...). */
@Entity
@Table(name = "co_scholastic_area")
@Getter
@Setter
@NoArgsConstructor
public class CoScholasticArea {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scheme_id", nullable = false)
    private UUID schemeId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "area_group", nullable = false)
    private AreaGroup areaGroup = AreaGroup.CO_SCHOLASTIC;

    @Column(name = "description")
    private String description;

    @Column(name = "sort_order")
    private int sortOrder;
}
