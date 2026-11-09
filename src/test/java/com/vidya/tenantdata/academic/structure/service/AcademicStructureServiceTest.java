package com.vidya.tenantdata.academic.structure.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.entity.Stream;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.enums.ClassStage;
import com.vidya.tenantdata.academic.enums.StreamStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.repository.StreamRepository;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.ClassStructureRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.SectionStreamsRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.SectionStreamsRequest.SectionStream;
import com.vidya.tenantdata.academic.structure.service.StructureTemplateService.Line;
import com.vidya.tenantdata.examination.repository.GradingScaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Academic structure — class settings, section streams, template")
class AcademicStructureServiceTest {

    @Mock private StreamRepository streamRepository;
    @Mock private AcademicClassRepository classRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private GradingScaleRepository gradingScaleRepository;
    @InjectMocks private AcademicStructureService service;

    private AcademicClass eleven;
    private AcademicClass five;
    private Stream science;

    @BeforeEach
    void setUp() {
        eleven = cls("CLASS_11", "Class XI", 11);
        eleven.setHasStreams(true);
        five = cls("CLASS_5", "Class V", 5);
        science = new Stream("SCIENCE", "Science", null, 1);
        science.setId(UUID.randomUUID());
        when(streamRepository.findByIdAndNotDeleted(science.getId())).thenReturn(Optional.of(science));
        when(classRepository.findByDeletedAtIsNullOrderBySortOrderAsc()).thenReturn(List.of(eleven, five));
    }

    @Test
    @DisplayName("class: stage follows grade level; a grade level is used by one class only")
    void updateClass() {
        AcademicClass c = cls("CLASS_7", "Class VII", null);
        AcademicClass r = service.updateClass(c.getId(), new ClassStructureRequest(null, 7, false, null));
        assertThat(r.getStage()).isEqualTo(ClassStage.MIDDLE);
        assertThat(r.getGradeLevel()).isEqualTo(7);

        assertThatThrownBy(() -> service.updateClass(c.getId(), new ClassStructureRequest(null, 5, false, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Class V");
    }

    @Test
    @DisplayName("section streams: only for classes with streams, active streams; one bad row changes nothing")
    void sectionStreams() {
        Section a = section(eleven, "A");
        Section b = section(five, "B");

        assertThat(service.setSectionStreams(new SectionStreamsRequest(List.of(new SectionStream(a.getId(), science.getId()))))).isEqualTo(1);
        assertThat(a.getStreamId()).isEqualTo(science.getId());

        a.setStreamId(null);
        assertThatThrownBy(() -> service.setSectionStreams(new SectionStreamsRequest(List.of(
                new SectionStream(a.getId(), science.getId()), new SectionStream(b.getId(), science.getId())))))
                .hasMessageContaining("does not use streams");
        assertThat(a.getStreamId()).isNull();

        science.setStatus(StreamStatus.INACTIVE);
        assertThatThrownBy(() -> service.setSectionStreams(new SectionStreamsRequest(List.of(new SectionStream(a.getId(), science.getId())))))
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("template: existing classes recognised by common names")
    void names() {
        assertThat(StructureTemplateService.matchesName("Class 5", 5)).isTrue();
        assertThat(StructureTemplateService.matchesName("Class V", 5)).isTrue();
        assertThat(StructureTemplateService.matchesName("Grade-5", 5)).isTrue();
        assertThat(StructureTemplateService.matchesName("5th", 5)).isTrue();
        assertThat(StructureTemplateService.matchesName("Std. XII", 12)).isTrue();
        assertThat(StructureTemplateService.matchesName("Lower KG", -1)).isTrue();
        assertThat(StructureTemplateService.matchesName("UKG", 0)).isTrue();
        assertThat(StructureTemplateService.matchesName("Class 15", 5)).isFalse();
        assertThat(StructureTemplateService.matchesName("Class 1", 11)).isFalse();
    }

    @Test
    @DisplayName("template curriculum: stream subjects only for chosen streams; Mathematics may repeat per stream")
    void lines() {
        List<Line> xi = StructureTemplateService.linesFor(11, ClassStage.SENIOR_SECONDARY, List.of("SCIENCE", "COMMERCE"));
        assertThat(xi).extracting(Line::stream).containsOnly(null, "SCIENCE", "COMMERCE");
        assertThat(xi).filteredOn(l -> l.subject().equals("Mathematics")).extracting(Line::stream).containsExactly("SCIENCE", "COMMERCE");
        assertThat(xi).extracting(Line::subject).doesNotContain("History");
        assertThat(StructureTemplateService.linesFor(2, ClassStage.PRIMARY, List.of())).extracting(Line::subject).doesNotContain("Environmental Studies");
        StructureTemplateService.linesFor(9, ClassStage.SECONDARY, List.of())
                .forEach(l -> assertThat(StructureTemplateService.SUBJECTS).containsKey(l.subject()));
    }

    private AcademicClass cls(String code, String name, Integer level) {
        AcademicClass c = new AcademicClass(code, name, name, level == null ? 0 : level);
        c.setId(UUID.randomUUID());
        c.setGradeLevel(level);
        when(classRepository.findByIdAndNotDeleted(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private Section section(AcademicClass c, String code) {
        AcademicYear y = new AcademicYear("AY27", "AY27", LocalDate.of(2027, 4, 1), LocalDate.of(2028, 3, 31), false, AcademicYearStatus.DRAFT);
        Section s = new Section(y, c, code, code, 40);
        s.setId(UUID.randomUUID());
        when(sectionRepository.findByIdAndNotDeleted(s.getId())).thenReturn(Optional.of(s));
        return s;
    }
}
