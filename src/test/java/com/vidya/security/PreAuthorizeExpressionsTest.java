package com.vidya.security;

import com.vidya.tenantdata.security.DataAccessService;
import com.vidya.security.service.TenantAuthorizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catches broken {@code @PreAuthorize} expressions at build time instead of as a 500 at runtime:
 * every expression must parse, every {@code #param} must be a parameter of the method, and every
 * {@code @dataAccess.x(...)} / {@code @tenantAuthorizationService.x(...)} must exist.
 */
@DisplayName("@PreAuthorize expressions are valid")
class PreAuthorizeExpressionsTest {

    private static final Pattern PARAM_REF = Pattern.compile("#([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern BEAN_CALL = Pattern.compile("@([a-zA-Z]+)\\.([a-zA-Z]+)\\(");

    @Test
    void allExpressionsParseAndReferenceRealParametersAndMethods() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        SpelExpressionParser parser = new SpelExpressionParser();
        Set<String> dataAccessMethods = Arrays.stream(DataAccessService.class.getMethods()).map(Method::getName).collect(Collectors.toSet());
        Set<String> tenantAuthMethods = Arrays.stream(TenantAuthorizationService.class.getMethods()).map(Method::getName).collect(Collectors.toSet());

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (var candidate : scanner.findCandidateComponents("com.vidya")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            for (Method m : type.getDeclaredMethods()) {
                PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
                if (pa == null) {
                    continue;
                }
                checked++;
                String where = type.getSimpleName() + "#" + m.getName();
                String expr = pa.value();
                try {
                    parser.parseExpression(expr);
                } catch (Exception e) {
                    problems.add(where + ": does not parse: " + e.getMessage());
                    continue;
                }
                Set<String> params = Arrays.stream(m.getParameters()).map(Parameter::getName).collect(Collectors.toSet());
                Matcher pm = PARAM_REF.matcher(expr);
                while (pm.find()) {
                    if (!params.contains(pm.group(1))) {
                        problems.add(where + ": references #" + pm.group(1) + " but parameters are " + params);
                    }
                }
                Matcher bm = BEAN_CALL.matcher(expr);
                while (bm.find()) {
                    Set<String> known = switch (bm.group(1)) {
                        case "dataAccess" -> dataAccessMethods;
                        case "tenantAuthorizationService" -> tenantAuthMethods;
                        default -> null;
                    };
                    if (known == null) {
                        problems.add(where + ": unknown bean @" + bm.group(1));
                    } else if (!known.contains(bm.group(2))) {
                        problems.add(where + ": @" + bm.group(1) + " has no method " + bm.group(2));
                    }
                }
            }
        }
        assertThat(checked).as("expressions checked").isGreaterThan(200);
        assertThat(problems).isEmpty();
    }
}
