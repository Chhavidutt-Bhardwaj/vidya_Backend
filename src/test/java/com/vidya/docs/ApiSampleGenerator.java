package com.vidya.docs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * Writes request / response samples for every REST endpoint (Markdown, one file per controller, grouped per module) and a plain
 * HTML + CSS sample page per module, by reading the controllers with reflection: paths, methods, permissions, path / query /
 * body parameters with their validation rules, and the response types. Sample values are made up from the field names and types.
 * Response codes and HTTP statuses come from the controller source. Run through {@link ApiSampleGeneratorTest}.
 */
final class ApiSampleGenerator {

    static final String BASE = "http://localhost:8080";
    static final String TENANT = "vgs";
    private static final Set<String> PUBLIC = Set.of("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout");
    private static final List<String> TENANT_EXEMPT = List.of("/api/v1/auth/", "/api/v1/onboarding", "/api/v1/subscription-plans",
            "/api/v1/school-subscriptions", "/api/v1/communities", "/api/v1/schools", "/api/v1/payments", "/api/v1/admin", "/api/v1/platform",
            "/api/v1/webhooks");

    /** Module folder → title, in the order the index shows them. */
    static final Map<String, String> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("security", "Login, sessions, roles & access");
        GROUPS.put("dashboard", "Overview dashboard");
        GROUPS.put("student", "Students (SIS, admission, enrolment, guardians)");
        GROUPS.put("staff", "Staff & HR set-up");
        GROUPS.put("user", "School users & accounts");
        GROUPS.put("academic", "Academic years, classes, sections, set-up");
        GROUPS.put("subject", "Subjects & curriculum");
        GROUPS.put("assignment", "Class teachers & subject teachers");
        GROUPS.put("timetable", "Timetable, bells, rooms, substitutions");
        GROUPS.put("calendar", "Calendar & holidays");
        GROUPS.put("attendance", "Student attendance");
        GROUPS.put("examination", "Exams, schedules, marks, grading");
        GROUPS.put("assessment", "Assessments, question bank, report cards");
        GROUPS.put("fee", "Fees & finance (counter, online, auto-debit)");
        GROUPS.put("engagement", "Communication, homework, daily report, parent portal");
        GROUPS.put("operations", "Operations set-up (fees, attendance, transport, library)");
        GROUPS.put("readiness", "School readiness");
        GROUPS.put("platform-school", "Platform: schools & campuses");
        GROUPS.put("platform-onboarding", "Platform: onboarding");
        GROUPS.put("platform-workflow", "Platform: onboarding workflow");
        GROUPS.put("platform-subscription", "Platform: subscriptions & plans");
        GROUPS.put("platform-payment", "Platform: subscription payments");
        GROUPS.put("platform-community", "Platform: communities (trusts / groups)");
        GROUPS.put("platform-hub", "Platform: hub");
        GROUPS.put("platform-analytics", "Platform: analytics");
        GROUPS.put("platform-support", "Platform: support queries");
    }

    private final Path mdRoot;
    private final Path htmlRoot;
    private final Path srcRoot = Path.of("src/main/java");
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final JsonNodeFactory nf = JsonNodeFactory.instance;
    private final DefaultParameterNameDiscoverer names = new DefaultParameterNameDiscoverer();

    ApiSampleGenerator(Path mdRoot, Path htmlRoot) {
        this.mdRoot = mdRoot;
        this.htmlRoot = htmlRoot;
    }

    // ── Model ────────────────────────────────────────────────────────────────────────

    record Param(String name, String in, Type type, boolean required, String defaultValue, String rules) {
    }

    record Endpoint(Class<?> controller, Method method, String http, String path, String summary, String description, String permission,
                    boolean authenticated, boolean tenant, List<Param> params, Type bodyType, boolean bodyRequired, boolean multipart,
                    Type returnType, int status, String code, String message, String fileType) {

        String anchor() {
            return (http + "-" + path).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        }
    }

    record Controller(Class<?> type, String group, String title, String description, List<Endpoint> endpoints) {
    }

    // ── Run ──────────────────────────────────────────────────────────────────────────

    void run() throws Exception {
        List<Controller> controllers = scan();
        Map<String, List<Controller>> byGroup = new TreeMap<>(Comparator.comparingInt(g -> {
            int i = new ArrayList<>(GROUPS.keySet()).indexOf(g);
            return i < 0 ? 999 : i;
        }));
        for (Controller c : controllers) {
            byGroup.computeIfAbsent(c.group(), g -> new ArrayList<>()).add(c);
        }
        Files.createDirectories(mdRoot);
        Files.createDirectories(htmlRoot);
        for (var e : byGroup.entrySet()) {
            Path dir = mdRoot.resolve(e.getKey());
            Files.createDirectories(dir);
            Map<String, String> errors = businessErrors(e.getValue());
            for (Controller c : e.getValue()) {
                Files.writeString(dir.resolve(c.type().getSimpleName() + ".md"), controllerMd(c, e.getKey()));
            }
            Files.writeString(dir.resolve("README.md"), groupMd(e.getKey(), e.getValue(), errors));
            Files.writeString(htmlRoot.resolve(e.getKey() + ".html"), groupHtml(e.getKey(), e.getValue(), byGroup.keySet()));
        }
        Files.writeString(mdRoot.resolve("README.md"), indexMd(byGroup));
        Files.writeString(mdRoot.resolve("00_COMMON.md"), commonMd());
        Files.writeString(htmlRoot.resolve("index.html"), indexHtml(byGroup));
        Files.writeString(htmlRoot.resolve("samples.css"), CSS);
    }

    List<Controller> scan() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Controller> out = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.vidya")) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            String source = source(type);
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String basePath = base == null || base.path().length == 0 ? "" : base.path()[0];
            PreAuthorize classAuth = type.getAnnotation(PreAuthorize.class);
            List<Endpoint> endpoints = new ArrayList<>();
            Method[] methods = type.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparingInt(m -> sourceIndex(source, m.getName())));
            for (Method m : methods) {
                RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (rm == null || !Modifier.isPublic(m.getModifiers())) {
                    continue;
                }
                String[] paths = rm.path().length == 0 ? new String[]{""} : rm.path();
                RequestMethod[] verbs = rm.method().length == 0 ? new RequestMethod[]{RequestMethod.GET} : rm.method();
                for (String p : paths) {
                    for (RequestMethod verb : verbs) {
                        endpoints.add(endpoint(type, m, verb.name(), (basePath + p).replaceAll("//+", "/"), classAuth, source));
                    }
                }
            }
            if (endpoints.isEmpty()) {
                continue;
            }
            Tag tag = type.getAnnotation(Tag.class);
            String title = tag != null && !tag.name().isBlank() ? tag.name() : humanize(type.getSimpleName().replace("Controller", ""));
            out.add(new Controller(type, group(type), title, tag == null ? "" : tag.description(), endpoints));
        }
        out.sort(Comparator.comparing(c -> c.type().getSimpleName()));
        return out;
    }

    static String group(Class<?> type) {
        String[] p = type.getPackageName().split("\\.");
        // com.vidya.<area>.<module>...
        String area = p.length > 2 ? p[2] : "misc";
        String module = p.length > 3 ? p[3] : area;
        return switch (area) {
            case "tenantdata" -> module;
            case "master" -> "platform-" + module;
            case "security" -> "security";
            default -> area;
        };
    }

    Endpoint endpoint(Class<?> type, Method m, String http, String path, PreAuthorize classAuth, String source) {
        Operation op = m.getAnnotation(Operation.class);
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        String permission = pa != null ? pa.value() : classAuth != null ? classAuth.value() : null;
        boolean open = PUBLIC.contains(path) || path.startsWith("/api/v1/webhooks/");
        boolean tenant = TENANT_EXEMPT.stream().noneMatch(path::startsWith);
        List<Param> params = new ArrayList<>();
        Type body = null;
        boolean bodyRequired = false;
        boolean multipart = false;
        String[] pnames = names.getParameterNames(m);
        Annotation[][] anns = m.getParameterAnnotations();
        Type[] types = m.getGenericParameterTypes();
        for (int i = 0; i < types.length; i++) {
            String pname = pnames != null ? pnames[i] : "arg" + i;
            Class<?> raw = raw(types[i]);
            PathVariable pv = find(anns[i], PathVariable.class);
            RequestParam rp = find(anns[i], RequestParam.class);
            RequestBody rb = find(anns[i], RequestBody.class);
            RequestHeader rh = find(anns[i], RequestHeader.class);
            RequestPart part = find(anns[i], RequestPart.class);
            if (pv != null) {
                params.add(new Param(first(pv.name(), pv.value(), pname), "path", types[i], true, null, rules(anns[i])));
            } else if (part != null) {
                multipart = true;
                params.add(new Param(first(part.name(), part.value(), pname), "form", types[i], part.required(), null, rules(anns[i])));
            } else if (rp != null) {
                boolean hasDefault = !ValueConstants.DEFAULT_NONE.equals(rp.defaultValue());
                if (MultipartFile.class.isAssignableFrom(raw) || isMultipartList(types[i])) {
                    multipart = true;
                    params.add(new Param(first(rp.name(), rp.value(), pname), "form", types[i], rp.required(), null, rules(anns[i])));
                } else {
                    params.add(new Param(first(rp.name(), rp.value(), pname), "query", types[i],
                            rp.required() && !hasDefault && raw != Optional.class, hasDefault ? rp.defaultValue() : null, rules(anns[i])));
                }
            } else if (rb != null) {
                body = types[i];
                bodyRequired = rb.required();
            } else if (rh != null) {
                params.add(new Param(first(rh.name(), rh.value(), pname), "header", types[i], rh.required()
                        && ValueConstants.DEFAULT_NONE.equals(rh.defaultValue()), null, ""));
            } else if (Pageable.class.isAssignableFrom(raw)) {
                PageableDefault pd = find(anns[i], PageableDefault.class);
                params.add(new Param("page", "query", int.class, false, "0", "0-based"));
                params.add(new Param("size", "query", int.class, false, pd == null ? "20" : String.valueOf(pd.size()), ""));
                params.add(new Param("sort", "query", String.class, false, pd == null || pd.sort().length == 0 ? null : String.join(",", pd.sort()),
                        "field,asc|desc"));
            } else if (ignoredParam(raw, anns[i])) {
                // framework / security argument
            } else if (raw.getName().startsWith("com.vidya")) {
                for (Prop prop : props(raw, Map.of())) {
                    params.add(new Param(prop.name(), "query", prop.type(), prop.required(), null, prop.rules()));
                }
            }
        }
        Type ret = m.getGenericReturnType();
        String window = methodSource(source, m.getName());
        String code = null;
        String message = null;
        Matcher cm = java.util.regex.Pattern.compile("\\b(?:success|of|ok|created|accepted)\\(\\s*(?:ApiResponseCode\\.)?\"?([A-Z][A-Z0-9_]+)\"?\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(window);
        if (cm.find()) {
            code = cm.group(1);
            message = cm.group(2);
        } else {
            Matcher cm2 = java.util.regex.Pattern.compile("\\b(?:success|of|ok|created|accepted)\\(\\s*(?:ApiResponseCode\\.)?\"?([A-Z][A-Z0-9_]+)").matcher(window);
            Matcher cm3 = java.util.regex.Pattern.compile("\\b(?:ok|created)\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(window);
            if (cm2.find()) {
                code = cm2.group(1);
            } else if (cm3.find()) {
                // controller helper ok("message", data) / created("message", data): the helper sends code SUCCESS
                code = "SUCCESS";
                message = cm3.group(1);
            }
        }
        int status = window.contains("HttpStatus.CREATED") || window.matches("(?s).*\\bcreated\\(.*") ? 201
                : window.contains("HttpStatus.ACCEPTED") || window.contains(".accepted(") ? 202
                : window.contains("noContent()") || window.contains("HttpStatus.NO_CONTENT") ? 204 : 200;
        String file = null;
        Class<?> inner = raw(unwrapEntity(ret));
        if (inner == byte[].class || org.springframework.core.io.Resource.class.isAssignableFrom(inner)
                || inner.getName().contains("StreamingResponseBody")) {
            Matcher mt = java.util.regex.Pattern.compile("(?:MediaType\\.(APPLICATION_PDF|APPLICATION_OCTET_STREAM|TEXT_PLAIN|IMAGE_PNG)"
                    + "|\"(text/csv|application/pdf|application/vnd[^\"]*|image/[a-z]+)\"|\"text\", \"csv\")").matcher(window);
            file = mt.find() ? (mt.group(1) != null ? mt.group(1).toLowerCase(Locale.ROOT).replace('_', '/') : mt.group(2) != null
                    ? mt.group(2) : "text/csv") : "application/octet-stream";
            if (file.equals("application/pdf") || file.equals("application/octet/stream")) {
                file = file.replace("octet/stream", "octet-stream");
            }
        }
        if (file != null) {
            Matcher fn = java.util.regex.Pattern.compile("filename=\\\\?\"([^\"\\\\]+)").matcher(window);
            if (fn.find()) {
                fileNames.put(m, fn.group(1));
            }
        }
        return new Endpoint(type, m, http, path, op == null ? "" : op.summary(), op == null ? "" : op.description(), permission, !open, tenant,
                params, body, bodyRequired, multipart, ret, status, code, message, file);
    }

    private static boolean isMultipartList(Type t) {
        return t instanceof ParameterizedType pt && Collection.class.isAssignableFrom(raw(pt)) && pt.getActualTypeArguments().length == 1
                && MultipartFile.class.isAssignableFrom(raw(pt.getActualTypeArguments()[0]));
    }

    private static boolean ignoredParam(Class<?> raw, Annotation[] anns) {
        String n = raw.getName();
        if (n.startsWith("jakarta.servlet") || n.startsWith("org.springframework.security") || n.startsWith("java.security")
                || n.startsWith("org.springframework.validation") || n.startsWith("org.springframework.ui") || n.startsWith("org.springframework.http")
                || n.startsWith("org.springframework.web") || n.equals("java.util.Locale") || n.startsWith("java.io")) {
            return true;
        }
        for (Annotation a : anns) {
            String s = a.annotationType().getSimpleName();
            if (s.contains("Principal") || s.contains("CurrentUser")) {
                return true;
            }
        }
        return false;
    }

    // ── Source helpers ───────────────────────────────────────────────────────────────

    String source(Class<?> type) throws IOException {
        Path p = srcRoot.resolve(type.getName().replace('.', '/') + ".java");
        return Files.exists(p) ? Files.readString(p) : "";
    }

    private static int sourceIndex(String source, String method) {
        Matcher m = java.util.regex.Pattern.compile("public\\s+[^;{=]*?\\b" + method + "\\s*\\(").matcher(source);
        return m.find() ? m.start() : Integer.MAX_VALUE;
    }

    private static String methodSource(String source, String method) {
        Matcher m = java.util.regex.Pattern.compile("public\\s+[^;{=]*?\\b" + method + "\\s*\\(").matcher(source);
        if (!m.find()) {
            return "";
        }
        int start = m.start();
        int end = source.indexOf("\n    }\n", start + 1);
        return source.substring(start, end < 0 ? source.length() : end);
    }

    /** Business error codes thrown in the modules' code (shown with HTTP 422). */
    Map<String, String> businessErrors(List<Controller> controllers) throws IOException {
        Map<String, String> out = new TreeMap<>();
        Set<Path> dirs = new LinkedHashSet<>();
        for (Controller c : controllers) {
            String[] p = c.type().getPackageName().split("\\.");
            String pkg = String.join("/", Arrays.copyOf(p, Math.min(p.length, 4)));
            dirs.add(srcRoot.resolve(pkg));
        }
        var pattern = java.util.regex.Pattern.compile(
                "(?:BusinessException|InvalidStateTransitionException|error|fail|reject|business)\\(\\s*\"([A-Z][A-Z0-9_]{3,})\"\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var files = Files.walk(dir)) {
                for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                    Matcher m = pattern.matcher(Files.readString(f));
                    while (m.find()) {
                        out.putIfAbsent(m.group(1), m.group(2).replace("\\\"", "\"").replace("|", "\\|"));
                    }
                }
            }
        }
        return out;
    }

    // ── Types → sample JSON ──────────────────────────────────────────────────────────

    record Prop(String name, Type type, boolean required, String rules, Field field) {
    }

    /** Properties as Jackson writes them: record components, else fields of the class and its superclasses. */
    List<Prop> props(Class<?> cls, Map<TypeVariable<?>, Type> vars) {
        List<Prop> out = new ArrayList<>();
        if (cls.isRecord()) {
            for (RecordComponent rc : cls.getRecordComponents()) {
                Field f = field(cls, rc.getName());
                if (ignored(f) || (rc.getAccessor() != null && rc.getAccessor().isAnnotationPresent(JsonIgnore.class))) {
                    continue;
                }
                out.add(new Prop(jsonName(f, rc.getName()), resolve(rc.getGenericType(), vars), required(f), rules(f), f));
            }
            return out;
        }
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            chain.addFirst(c);
        }
        for (Class<?> c : chain) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()) || ignored(f) || f.isSynthetic()) {
                    continue;
                }
                String n = f.getName();
                if (f.getType() == boolean.class && n.length() > 2 && n.startsWith("is") && Character.isUpperCase(n.charAt(2))) {
                    n = Character.toLowerCase(n.charAt(2)) + n.substring(3);
                }
                out.add(new Prop(jsonName(f, n), resolve(f.getGenericType(), vars), required(f), rules(f), f));
            }
        }
        return out;
    }

    private static Field field(Class<?> c, String name) {
        try {
            return c.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static boolean ignored(Field f) {
        return f != null && f.isAnnotationPresent(JsonIgnore.class);
    }

    private static String jsonName(Field f, String fallback) {
        JsonProperty jp = f == null ? null : f.getAnnotation(JsonProperty.class);
        return jp != null && !jp.value().isEmpty() ? jp.value() : fallback;
    }

    private static boolean required(Field f) {
        return f != null && (f.isAnnotationPresent(NotNull.class) || f.isAnnotationPresent(NotBlank.class) || f.isAnnotationPresent(NotEmpty.class));
    }

    private static String rules(Field f) {
        return f == null ? "" : rules(f.getAnnotations());
    }

    static String rules(Annotation[] anns) {
        List<String> r = new ArrayList<>();
        for (Annotation a : anns) {
            switch (a) {
                case NotBlank x -> r.add("not blank");
                case NotEmpty x -> r.add("not empty");
                case Size s -> r.add(s.min() > 0 && s.max() < Integer.MAX_VALUE ? "size " + s.min() + "–" + s.max()
                        : s.max() < Integer.MAX_VALUE ? "max " + s.max() : "min " + s.min());
                case Min x -> r.add("≥ " + x.value());
                case Max x -> r.add("≤ " + x.value());
                case DecimalMin x -> r.add("≥ " + x.value());
                case DecimalMax x -> r.add("≤ " + x.value());
                case Positive x -> r.add("> 0");
                case PositiveOrZero x -> r.add("≥ 0");
                case Email x -> r.add("e-mail");
                case Pattern p -> r.add("pattern `" + p.regexp().replace("|", "\\|") + "`");
                default -> {
                    String n = a.annotationType().getSimpleName();
                    if (Set.of("Past", "PastOrPresent", "Future", "FutureOrPresent", "Digits", "Negative").contains(n)) {
                        r.add(humanize(n).toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return String.join(", ", r);
    }

    static <A extends Annotation> A find(Annotation[] anns, Class<A> type) {
        for (Annotation a : anns) {
            if (type.isInstance(a)) {
                return type.cast(a);
            }
            A merged = AnnotatedElementUtils.findMergedAnnotation(a.annotationType(), type);
            if (merged != null && a.annotationType().isAnnotationPresent(type)) {
                return merged;
            }
        }
        return null;
    }

    static Class<?> raw(Type t) {
        return switch (t) {
            case Class<?> c -> c;
            case ParameterizedType p -> (Class<?>) p.getRawType();
            case GenericArrayType g -> java.lang.reflect.Array.newInstance(raw(g.getGenericComponentType()), 0).getClass();
            case WildcardType w -> raw(w.getUpperBounds()[0]);
            case TypeVariable<?> v -> raw(v.getBounds()[0]);
            default -> Object.class;
        };
    }

    static Type arg(Type t, int i) {
        return t instanceof ParameterizedType p && p.getActualTypeArguments().length > i ? p.getActualTypeArguments()[i] : Object.class;
    }

    static Type resolve(Type t, Map<TypeVariable<?>, Type> vars) {
        if (t instanceof TypeVariable<?> v && vars.containsKey(v)) {
            return vars.get(v);
        }
        if (t instanceof ParameterizedType p) {
            Type[] args = Arrays.stream(p.getActualTypeArguments()).map(a -> resolve(a, vars)).toArray(Type[]::new);
            return new SimpleParameterizedType((Class<?>) p.getRawType(), args);
        }
        if (t instanceof WildcardType w) {
            return resolve(w.getUpperBounds()[0], vars);
        }
        return t;
    }

    record SimpleParameterizedType(Class<?> rawType, Type[] args) implements ParameterizedType {
        @Override
        public Type[] getActualTypeArguments() {
            return args;
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }

    static Type unwrapEntity(Type t) {
        return ResponseEntity.class.isAssignableFrom(raw(t)) ? arg(t, 0) : t;
    }

    /** Enums met while building samples, for the "values" section of each file. */
    final Map<Class<?>, List<String>> enumsSeen = new TreeMap<>(Comparator.comparing(Class::getSimpleName));

    JsonNode sample(Type type, String name, Class<?> owner, int variant, int depth, Set<Class<?>> stack) {
        Class<?> c = raw(type);
        String n = name == null ? "" : name;
        if (depth > 16) {
            return nf.nullNode();
        }
        if (c == String.class || c == CharSequence.class) {
            return nf.textNode(text(n, owner, variant));
        }
        if (c == UUID.class) {
            return nf.textNode(uuid(n, owner, variant));
        }
        if (c == boolean.class || c == Boolean.class) {
            return nf.booleanNode(!(n.startsWith("is") && n.toLowerCase(Locale.ROOT).contains("deleted")) && variant != 2);
        }
        if (c == int.class || c == Integer.class || c == long.class || c == Long.class || c == short.class || c == Short.class
                || c == byte.class || c == Byte.class || c == BigInteger.class) {
            return nf.numberNode(integer(n, variant));
        }
        if (c == double.class || c == Double.class || c == float.class || c == Float.class || c == BigDecimal.class) {
            return nf.numberNode(decimal(n, variant));
        }
        if (c == LocalDate.class) {
            return nf.textNode(date(n, variant).toString());
        }
        if (c == LocalDateTime.class) {
            return nf.textNode(date(n, variant) + "T10:00:00");
        }
        if (c == Instant.class || c == OffsetDateTime.class || c == ZonedDateTime.class || c == java.util.Date.class) {
            return nf.textNode(date(n, variant) + "T04:30:00Z");
        }
        if (c == LocalTime.class) {
            String l = n.toLowerCase(Locale.ROOT);
            return nf.textNode(l.contains("end") || l.contains("to") ? "09:45:00" : "09:00:00");
        }
        if (c == YearMonth.class) {
            return nf.textNode("2026-11");
        }
        if (c == Year.class) {
            return nf.numberNode(2026);
        }
        if (c == Duration.class) {
            return nf.textNode("PT45M");
        }
        if (c == ZoneId.class) {
            return nf.textNode("Asia/Kolkata");
        }
        if (c == DayOfWeek.class) {
            enumsSeen.put(DayOfWeek.class, Arrays.stream(DayOfWeek.values()).map(Enum::name).toList());
            return nf.textNode(DayOfWeek.values()[variant % 5].name());
        }
        if (c.isEnum()) {
            Object[] values = c.getEnumConstants();
            enumsSeen.put(c, Arrays.stream(values).map(v -> ((Enum<?>) v).name()).toList());
            return values.length == 0 ? nf.nullNode() : nf.textNode(((Enum<?>) values[variant % values.length]).name());
        }
        if (c == byte[].class) {
            return nf.textNode("JVBERi0xLjQKJcOkw7zDtsOf");
        }
        if (MultipartFile.class.isAssignableFrom(c)) {
            return nf.textNode("(binary file)");
        }
        if (c.isArray()) {
            ArrayNode a = nf.arrayNode();
            a.add(sample(c.getComponentType(), singular(n), owner, variant, depth + 1, stack));
            return a;
        }
        if (Optional.class.isAssignableFrom(c)) {
            return sample(arg(type, 0), n, owner, variant, depth, stack);
        }
        if (Page.class.isAssignableFrom(c)) {
            return page(arg(type, 0), n, owner, depth, stack);
        }
        if (Collection.class.isAssignableFrom(c)) {
            ArrayNode a = nf.arrayNode();
            Type el = arg(type, 0);
            a.add(sample(el, singular(n), owner, variant, depth + 1, stack));
            if (depth <= 3 && !isSimple(raw(el))) {
                a.add(sample(el, singular(n), owner, variant + 1, depth + 1, stack));
            }
            return a;
        }
        if (Map.class.isAssignableFrom(c)) {
            ObjectNode o = nf.objectNode();
            JsonNode key = sample(arg(type, 0), singular(n) + "Key", owner, variant, depth + 1, stack);
            o.set(key.isTextual() ? key.asText() : "key", sample(arg(type, 1), singular(n), owner, variant, depth + 1, stack));
            return o;
        }
        if (c == Object.class || JsonNode.class.isAssignableFrom(c) || c.getName().startsWith("tools.jackson")) {
            ObjectNode o = nf.objectNode();
            o.put("key", "value");
            return o;
        }
        if (c == ApiResponse.class) {
            return envelope(arg(type, 0), "SUCCESS", "OK", owner, depth, stack);
        }
        if (c.isInterface() || Modifier.isAbstract(c.getModifiers())) {
            if (c.isSealed() && c.getPermittedSubclasses().length > 0) {
                return sample(c.getPermittedSubclasses()[0], n, owner, variant, depth, stack);
            }
            return nf.objectNode();
        }
        if (stack.contains(c)) {
            return nf.nullNode();
        }
        stack.add(c);
        try {
            ObjectNode o = nf.objectNode();
            Map<TypeVariable<?>, Type> vars = new HashMap<>();
            TypeVariable<?>[] tv = c.getTypeParameters();
            for (int i = 0; i < tv.length; i++) {
                vars.put(tv[i], arg(type, i));
            }
            for (Prop p : props(c, vars)) {
                o.set(p.name(), sample(p.type(), p.name(), c, variant, depth + 1, stack));
            }
            return o;
        } finally {
            stack.remove(c);
        }
    }

    ObjectNode page(Type element, String name, Class<?> owner, int depth, Set<Class<?>> stack) {
        ObjectNode p = nf.objectNode();
        ArrayNode content = p.putArray("content");
        content.add(sample(element, singular(name), owner, 0, depth + 1, stack));
        content.add(sample(element, singular(name), owner, 1, depth + 1, stack));
        ObjectNode pageable = p.putObject("pageable");
        pageable.put("pageNumber", 0);
        pageable.put("pageSize", 20);
        ObjectNode sort = pageable.putObject("sort");
        sort.put("empty", true);
        sort.put("sorted", false);
        sort.put("unsorted", true);
        pageable.put("offset", 0);
        pageable.put("paged", true);
        pageable.put("unpaged", false);
        p.put("last", true);
        p.put("totalElements", 2);
        p.put("totalPages", 1);
        p.put("size", 20);
        p.put("number", 0);
        p.set("sort", sort.deepCopy());
        p.put("numberOfElements", 2);
        p.put("first", true);
        p.put("empty", false);
        return p;
    }

    ObjectNode envelope(Type data, String code, String message, Class<?> owner, int depth, Set<Class<?>> stack) {
        ObjectNode o = nf.objectNode();
        o.put("success", true);
        o.put("code", code);
        o.put("message", message);
        Class<?> d = raw(data);
        if (d != Void.class && d != Object.class) {
            o.set("data", sample(data, "data", owner, 0, depth + 1, stack));
        }
        o.put("timestamp", "2026-11-22T04:30:00Z");
        return o;
    }

    /** The HTTP body of a successful call, as the client receives it. */
    JsonNode responseBody(Endpoint e) {
        Type t = unwrapEntity(e.returnType());
        Class<?> c = raw(t);
        String code = e.code() == null ? "SUCCESS" : e.code();
        String msg = e.message() == null || e.message().isBlank() ? humanize(code).replace("_", " ") : e.message();
        if (c == ApiResponse.class) {
            return envelope(arg(t, 0), code, msg, e.controller(), 0, new LinkedHashSet<>());
        }
        if (c == PagedApiResponse.class) {
            ObjectNode o = nf.objectNode();
            o.put("success", true);
            o.put("code", code);
            o.put("message", msg);
            ArrayNode data = o.putArray("data");
            data.add(sample(arg(t, 0), "item", e.controller(), 0, 1, new LinkedHashSet<>()));
            data.add(sample(arg(t, 0), "item", e.controller(), 1, 1, new LinkedHashSet<>()));
            ObjectNode page = o.putObject("page");
            page.put("number", 0);
            page.put("size", 20);
            page.put("totalElements", 2);
            page.put("totalPages", 1);
            page.put("first", true);
            page.put("last", true);
            o.put("timestamp", "2026-11-22T04:30:00Z");
            return o;
        }
        if (c == void.class || c == Void.class) {
            return null;
        }
        return sample(t, "data", e.controller(), 0, 0, new LinkedHashSet<>());
    }

    static boolean isSimple(Class<?> c) {
        return c.isPrimitive() || c.isEnum() || c == String.class || c == UUID.class || Number.class.isAssignableFrom(c) || c == Boolean.class
                || c.getName().startsWith("java.time") || c == Character.class;
    }

    // ── Sample values from names ─────────────────────────────────────────────────────

    private static final String[] STUDENTS = {"Riya Rao", "Aman Verma", "Kavya Iyer"};
    private static final String[] FIRST = {"Riya", "Aman", "Kavya"};
    private static final String[] LAST = {"Rao", "Verma", "Iyer"};
    private static final String[] PARENTS = {"Asha Rao", "Rakesh Verma", "Meena Iyer"};
    private static final String[] STAFF = {"Neha Sharma", "Vikram Singh", "Farah Khan"};
    private static final String[] CLASSES = {"Class 9", "Class 4", "Class 10"};
    private static final String[] SUBJECTS = {"Mathematics", "Science", "English"};

    static String uuid(String name, Class<?> owner, int variant) {
        String key = name.equalsIgnoreCase("id") && owner != null ? owner.getSimpleName() + "Id" : name;
        return UUID.nameUUIDFromBytes((key.toLowerCase(Locale.ROOT) + ":" + variant).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String text(String name, Class<?> owner, int v) {
        String n = name.toLowerCase(Locale.ROOT);
        String ownerName = owner == null ? "item" : humanize(owner.getSimpleName().replaceAll("(View|Response|Request|Dto|DTO|Row|Item|Summary|Detail|Details|Result|Create|Update|Upsert)", ""));
        if (n.endsWith("id") && !n.endsWith("paid") && !n.endsWith("valid") && n.length() > 2) {
            return uuid(name, owner, v);
        }
        if (n.contains("email")) {
            return new String[]{"asha.rao@example.com", "rakesh.verma@example.com", "meena.iyer@example.com"}[v % 3];
        }
        if (n.contains("phone") || n.contains("mobile") || n.contains("whatsapp")) {
            return new String[]{"9811111121", "9822222232", "9833333343"}[v % 3];
        }
        if (n.contains("password")) {
            return "Str0ng!Pass2026";
        }
        if (n.contains("otp")) {
            return "482913";
        }
        if (n.contains("token")) {
            return "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.c2FtcGxlLXNpZ25hdHVyZQ";
        }
        if (n.contains("signature") || n.contains("hash")) {
            return "5d41402abc4b2a76b9719d911017c592";
        }
        if (n.contains("url") || n.contains("link") || n.contains("href")) {
            return "https://app.vidya.io/" + ownerName.toLowerCase(Locale.ROOT).replace(' ', '-') + "/" + (v + 1);
        }
        if (n.contains("firstname")) {
            return FIRST[v % 3];
        }
        if (n.contains("lastname") || n.contains("surname")) {
            return LAST[v % 3];
        }
        if (n.contains("middlename")) {
            return "K";
        }
        if (n.contains("student") && n.contains("name") || n.equals("displayname") || n.equals("fullname")) {
            return STUDENTS[v % 3];
        }
        if ((n.contains("guardian") || n.contains("parent") || n.contains("payer") || n.contains("father") || n.contains("mother")) && n.contains("name")) {
            return PARENTS[v % 3];
        }
        if ((n.contains("teacher") || n.contains("staff") || n.contains("by") || n.contains("employee") || n.contains("reviewer")
                || n.contains("approver") || n.contains("actor") || n.contains("user")) && n.contains("name")) {
            return STAFF[v % 3];
        }
        if (n.contains("class") && n.contains("name")) {
            return CLASSES[v % 3];
        }
        if (n.contains("section") && n.contains("name")) {
            return new String[]{"A", "B", "C"}[v % 3];
        }
        if (n.contains("subject") && n.contains("name")) {
            return SUBJECTS[v % 3];
        }
        if (n.contains("school") && n.contains("name")) {
            return "Vidya Global School";
        }
        if (n.contains("year") && n.contains("name") || n.equals("academicyear")) {
            return "2026-27";
        }
        if (n.contains("admissionnumber") || n.contains("admissionno")) {
            return "VGS/2019/" + (101 + v);
        }
        if (n.contains("roll")) {
            return String.valueOf(12 + v);
        }
        if (n.contains("employeecode") || n.contains("staffcode") || n.contains("employeeid")) {
            return "EMP-" + (42 + v);
        }
        if (n.contains("invoice")) {
            return "INV/26/0010" + (1 + v);
        }
        if (n.contains("receipt")) {
            return "RCP/26/0481" + (2 + v);
        }
        if (n.contains("tenant")) {
            return TENANT;
        }
        if (n.contains("pincode") || n.contains("zip") || n.contains("postal")) {
            return "122003";
        }
        if (n.contains("contact") && n.contains("name") || n.contains("emergency") && n.contains("name")) {
            return PARENTS[v % 3];
        }
        if (n.contains("website")) {
            return "https://www.vgs.edu.in";
        }
        if (n.contains("ipaddress") || n.equals("ip")) {
            return "103.21.58." + (14 + v);
        }
        if (n.contains("useragent")) {
            return "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/537.36 Chrome/131.0 Safari/537.36";
        }
        if (n.contains("browser")) {
            return new String[]{"Chrome 131", "Safari 18", "Firefox 133"}[v % 3];
        }
        if (n.contains("device") || n.equals("os")) {
            return new String[]{"macOS", "Android", "Windows"}[v % 3];
        }
        if (n.equals("grade") || n.endsWith("grade")) {
            return new String[]{"A1", "B2", "A2"}[v % 3];
        }
        if (n.contains("language") || n.equals("medium")) {
            return "English";
        }
        if (n.contains("landmark")) {
            return "Near City Park";
        }
        if (n.contains("department")) {
            return new String[]{"Science", "Administration", "Languages"}[v % 3];
        }
        if (n.contains("designation")) {
            return new String[]{"PGT Mathematics", "Accountant", "TGT English"}[v % 3];
        }
        if (n.contains("qualification")) {
            return "M.Sc., B.Ed.";
        }
        if (n.contains("industry")) {
            return "Education";
        }
        if (n.equals("stem") || n.contains("question")) {
            return "What is 3/4 + 1/8?";
        }
        if (n.contains("homework")) {
            return "Exercise 4.2, questions 1 to 10";
        }
        if (n.contains("objective") || n.contains("outcome")) {
            return "Add fractions with unlike denominators";
        }
        if (n.contains("method")) {
            return "Activity based";
        }
        if (n.contains("resource")) {
            return "NCERT Class 6 Mathematics, chapter 4";
        }
        if (n.contains("paymentterms")) {
            return "Net 15 days";
        }
        if (n.contains("setlabel")) {
            return "Set " + (char) ('A' + v);
        }
        if (n.equals("source") || n.endsWith("source")) {
            return "WEBSITE";
        }
        if (n.equals("subject")) {
            return "Fee reminder for November";
        }
        if (n.contains("level")) {
            return "Level " + (v + 1);
        }
        if (n.contains("remark") || n.contains("review")) {
            return new String[]{"Checked and approved", "Please correct the dates", "Verified with the class teacher"}[v % 3];
        }
        if (n.contains("note")) {
            return new String[]{"Parent informed on phone", "Follow up next week", "No action needed"}[v % 3];
        }
        if (n.contains("reason")) {
            return new String[]{"Requested by the parent", "Entered by mistake", "Medical leave"}[v % 3];
        }
        if (n.contains("instruction")) {
            return "Answer all questions. Calculators are not allowed.";
        }
        if (n.contains("message") || n.equals("body") || n.endsWith("body")) {
            return "Please bring the signed consent slip by Friday.";
        }
        if (n.endsWith("by") || n.endsWith("byname")) {
            return STAFF[v % 3];
        }
        if (n.contains("explanation")) {
            return "Common denominator is 8, so 6/8 + 1/8 = 7/8.";
        }
        if (n.contains("occupation") || n.contains("profession")) {
            return new String[]{"Engineer", "Doctor", "Business owner"}[v % 3];
        }
        if (n.contains("region") || n.contains("zone")) {
            return "North";
        }
        if (n.contains("location") || n.contains("venue")) {
            return "Main building, 2nd floor";
        }
        if (n.equals("error") || n.endsWith("error") || n.contains("errors")) {
            return "Row 4: admission number is missing";
        }
        if (n.contains("warning")) {
            return "2 students have no roll number";
        }
        if (n.equals("section") || n.equals("tosection") || n.equals("fromsection")) {
            return new String[]{"A", "B", "C"}[v % 3];
        }
        if (n.equals("block") || n.contains("building")) {
            return "Block A";
        }
        if (n.equals("api")) {
            return "POST /api/v1/sample/" + (v + 1) + "/approve";
        }
        if (n.contains("operatingsystem")) {
            return "macOS 14";
        }
        if (n.contains("feedback")) {
            return "Good progress this term";
        }
        if (n.contains("examterm") || n.equals("term")) {
            return "Term 1";
        }
        if (n.contains("unit")) {
            return "Unit 4: Fractions";
        }
        if (n.contains("topic") || n.contains("chapter")) {
            return "Fractions";
        }
        if (n.contains("sectiontitle")) {
            return "Section A";
        }
        if (n.equals("assessment") || n.equals("exam")) {
            return "Unit test 2";
        }
        if (n.contains("reflection")) {
            return "Students needed more practice with word problems";
        }
        if (n.contains("class")) {
            return CLASSES[v % 3];
        }
        if (n.contains("subcategory")) {
            return "General";
        }
        if (n.contains("scope")) {
            return "Whole school";
        }
        if (n.equals("description") || n.endsWith("description")) {
            return ownerName + " for the 2026-27 session";
        }
        if (n.equals("code") || n.endsWith("code")) {
            String p = ownerName.replaceAll("[^A-Za-z ]", "").trim();
            String initials = Arrays.stream(p.split(" ")).filter(s -> !s.isEmpty()).map(s -> s.substring(0, 1)).collect(Collectors.joining())
                    .toUpperCase(Locale.ROOT);
            return (initials.isEmpty() ? "C" : initials) + "-00" + (1 + v);
        }
        if (n.contains("line1") || n.equals("address") || n.contains("street")) {
            return "12 MG Road";
        }
        if (n.contains("line2")) {
            return "Near City Park";
        }
        if (n.contains("city") || n.contains("district")) {
            return "Gurugram";
        }
        if (n.contains("state")) {
            return "Haryana";
        }
        if (n.contains("country")) {
            return "India";
        }
        if (n.contains("gender")) {
            return new String[]{"FEMALE", "MALE", "FEMALE"}[v % 3];
        }
        if (n.contains("blood")) {
            return "B+";
        }
        if (n.contains("ifsc")) {
            return "HDFC0001234";
        }
        if (n.contains("account") && n.contains("number")) {
            return "50100012345678";
        }
        if (n.contains("pan")) {
            return "ABCDE1234F";
        }
        if (n.contains("aadhaar") || n.contains("aadhar")) {
            return "XXXX-XXXX-1234";
        }
        if (n.contains("apaar")) {
            return "123456789012";
        }
        if (n.contains("color") || n.contains("colour")) {
            return "#2F5BEA";
        }
        if (n.contains("timezone") || n.equals("zone")) {
            return "Asia/Kolkata";
        }
        if (n.contains("currency")) {
            return "INR";
        }
        if (n.contains("month")) {
            return "2026-11";
        }
        if (n.contains("date") || name.endsWith("On") || name.endsWith("At")) {
            return date(n, v).toString();
        }
        if (n.contains("time")) {
            return "09:00";
        }
        if (n.contains("status") || n.equals("state")) {
            return "ACTIVE";
        }
        if (n.equals("type") || n.endsWith("type") || n.equals("kind") || n.equals("category")) {
            return "REGULAR";
        }
        if (n.contains("role")) {
            return "SCHOOL_ADMIN";
        }
        if (n.contains("permission") || n.contains("authority")) {
            return "STUDENT_READ";
        }
        if (n.contains("reference") || n.endsWith("ref") || n.endsWith("number") || n.endsWith("no")) {
            return "REF-2026-000" + (1 + v);
        }
        if (n.contains("board")) {
            return "CBSE";
        }
        if (n.contains("q") && n.length() <= 6 || n.contains("search") || n.contains("query")) {
            return "riya";
        }
        if (n.contains("sort")) {
            return "createdAt,desc";
        }
        if (n.equals("name") || n.endsWith("name") || n.equals("label") || n.equals("title")) {
            return ownerName.isBlank() ? "Sample " + (v + 1) : ownerName + " " + (v + 1);
        }
        if (n.contains("description") || n.contains("remark") || n.contains("reason") || n.contains("note") || n.contains("comment")
                || n.contains("message") || n.contains("body") || n.contains("content") || n.contains("summary") || n.contains("detail")
                || n.contains("instruction") || n.contains("text")) {
            return "Sample " + humanize(name).toLowerCase(Locale.ROOT) + (v == 0 ? "" : " " + (v + 1));
        }
        if (n.contains("file") || n.contains("path") || n.contains("key")) {
            return "uploads/2026/11/" + ownerName.toLowerCase(Locale.ROOT).replace(' ', '-') + "-" + (v + 1) + ".pdf";
        }
        return "Sample " + humanize(name).toLowerCase(Locale.ROOT) + (v == 0 ? "" : " " + (v + 1));
    }

    static long integer(String name, int v) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("paise")) {
            return new long[]{250_000, 180_000, 320_000}[v % 3];
        }
        if (n.equals("page") || n.endsWith("offset") || n.equals("number") || n.equals("version")) {
            return 0;
        }
        if (n.equals("size") || n.endsWith("limit")) {
            return 20;
        }
        if (n.contains("year")) {
            return 2026;
        }
        if (n.contains("month")) {
            return 11;
        }
        if (n.contains("day")) {
            return 10;
        }
        if (n.contains("capacity") || n.contains("seats")) {
            return 40;
        }
        if (n.contains("minute") || n.contains("duration")) {
            return 45;
        }
        if (n.contains("marks") || n.contains("score")) {
            return 78 - v * 6;
        }
        if (n.contains("max")) {
            return 100;
        }
        if (n.contains("order") || n.contains("rank") || n.contains("sequence") || n.contains("position") || n.contains("period")
                || n.contains("no") || n.contains("attempt") || n.contains("level")) {
            return 1 + v;
        }
        if (n.contains("age")) {
            return 14;
        }
        if (n.contains("amount") || n.contains("fee") || n.contains("price") || n.contains("salary")) {
            return new long[]{2500, 1800, 3200}[v % 3];
        }
        if (n.contains("count") || n.contains("total") || n.contains("students") || n.contains("present") || n.contains("absent")) {
            return new long[]{42, 37, 18}[v % 3];
        }
        return 1 + v;
    }

    static BigDecimal decimal(String name, int v) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("percent") || n.contains("rate") || n.contains("ratio") || n.contains("occupancy")) {
            return new BigDecimal(new String[]{"85.5", "92.0", "76.3"}[v % 3]);
        }
        if (n.contains("lat")) {
            return new BigDecimal("28.4595");
        }
        if (n.contains("lon") || n.contains("lng")) {
            return new BigDecimal("77.0266");
        }
        if (n.contains("marks") || n.contains("score")) {
            return new BigDecimal(new String[]{"78.5", "64.0", "91.0"}[v % 3]);
        }
        if (n.contains("amount") || n.contains("fee") || n.contains("price") || n.contains("salary") || n.contains("fine")) {
            return new BigDecimal(new String[]{"2500.00", "1800.00", "3200.00"}[v % 3]);
        }
        if (n.contains("weight")) {
            return new BigDecimal("0.5");
        }
        return new BigDecimal(new String[]{"10.5", "8.0", "12.25"}[v % 3]);
    }

    static LocalDate date(String name, int v) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("birth") || n.equals("dob")) {
            return LocalDate.of(2012, 5, 14).plusYears(v);
        }
        if (n.contains("end") || n.equals("to") || n.contains("till") || n.contains("until") || n.contains("expir") || n.contains("valid")) {
            return LocalDate.of(2027, 3, 31);
        }
        if (n.contains("start") || n.equals("from") || n.contains("joining") || n.contains("admission")) {
            return LocalDate.of(2026, 4, 1);
        }
        if (n.contains("due")) {
            return LocalDate.of(2026, 12, 10);
        }
        return LocalDate.of(2026, 11, 22).minusDays(v);
    }

    static String singular(String n) {
        if (n.endsWith("ies")) {
            return n.substring(0, n.length() - 3) + "y";
        }
        if (n.endsWith("ses") || n.endsWith("xes")) {
            return n.substring(0, n.length() - 2);
        }
        return n.endsWith("s") && !n.endsWith("ss") ? n.substring(0, n.length() - 1) : n;
    }

    static String humanize(String s) {
        String spaced = s.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').trim();
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1).toLowerCase(Locale.ROOT);
    }

    static String first(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    String typeLabel(Type t) {
        Class<?> c = raw(t);
        if (c == UUID.class) {
            return "uuid";
        }
        if (c == LocalDate.class) {
            return "date";
        }
        if (c == Instant.class || c == OffsetDateTime.class || c == LocalDateTime.class || c == ZonedDateTime.class) {
            return "date-time";
        }
        if (c == LocalTime.class) {
            return "time";
        }
        if (c == YearMonth.class) {
            return "YYYY-MM";
        }
        if (c.isEnum()) {
            enumsSeen.put(c, Arrays.stream(c.getEnumConstants()).map(v -> ((Enum<?>) v).name()).toList());
            return "enum `" + c.getSimpleName() + "`";
        }
        if (Collection.class.isAssignableFrom(c) || c.isArray()) {
            return "array of " + typeLabel(c.isArray() ? c.getComponentType() : arg(t, 0));
        }
        if (Map.class.isAssignableFrom(c)) {
            return "object (map)";
        }
        if (Optional.class.isAssignableFrom(c)) {
            return typeLabel(arg(t, 0));
        }
        if (MultipartFile.class.isAssignableFrom(c)) {
            return "file";
        }
        if (c == int.class || c == Integer.class || c == long.class || c == Long.class || c == short.class || c == Short.class) {
            return "integer";
        }
        if (c == BigDecimal.class || c == double.class || c == Double.class || c == float.class || c == Float.class) {
            return "number";
        }
        if (c == boolean.class || c == Boolean.class) {
            return "boolean";
        }
        if (c == String.class) {
            return "string";
        }
        return "object `" + c.getSimpleName() + "`";
    }

    // ── Markdown ─────────────────────────────────────────────────────────────────────

    String controllerMd(Controller c, String group) throws Exception {
        enumsSeen.clear();
        StringBuilder md = new StringBuilder();
        md.append("# ").append(c.title()).append("\n\n");
        if (!c.description().isBlank()) {
            md.append(c.description()).append("\n\n");
        }
        md.append("Source: `").append(c.type().getName().replace('.', '/')).append(".java` · Common rules, envelope and errors: [`../00_COMMON.md`](../00_COMMON.md) · ")
                .append("Business error codes of this module: [`README.md`](README.md#business-errors-http-422) · Sample page: `templates/samples/")
                .append(group).append(".html`\n\n");
        md.append("| # | Method | Path | What | Permission |\n|---|---|---|---|---|\n");
        int i = 1;
        for (Endpoint e : c.endpoints()) {
            md.append("| ").append(i++).append(" | `").append(e.http()).append("` | [`").append(e.path()).append("`](#").append(e.anchor()).append(") | ")
                    .append(cell(e.summary().isBlank() ? humanize(e.method().getName()) : e.summary())).append(" | ")
                    .append(e.permission() == null ? (e.authenticated() ? "signed in" : "public") : "`" + cell(e.permission()) + "`").append(" |\n");
        }
        md.append("\n---\n");
        for (Endpoint e : c.endpoints()) {
            md.append(endpointMd(e));
        }
        if (!enumsSeen.isEmpty()) {
            md.append("\n## Enum values used above\n\n| Enum | Values |\n|---|---|\n");
            enumsSeen.forEach((k, v) -> md.append("| `").append(k.getSimpleName()).append("` | ")
                    .append(v.stream().map(x -> "`" + x + "`").collect(Collectors.joining(", "))).append(" |\n"));
        }
        return md.toString();
    }

    String endpointMd(Endpoint e) throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("\n<a id=\"").append(e.anchor()).append("\"></a>\n\n## ").append(e.http()).append(" ").append(e.path()).append("\n\n");
        if (!e.summary().isBlank()) {
            md.append("**").append(e.summary()).append(".** ");
        }
        if (!e.description().isBlank()) {
            md.append(e.description());
        }
        md.append("\n\n| | |\n|---|---|\n");
        md.append("| Permission | ").append(e.permission() == null ? (e.authenticated() ? "any signed-in user" : "none (public)") : "`" + cell(e.permission()) + "`").append(" |\n");
        md.append("| School (tenant) | ").append(e.tenant() ? "yes: from the token, or header `X-Tenant-ID`" : "no (platform / master data)").append(" |\n");
        md.append("| Success | `").append(e.status()).append("`").append(e.code() != null ? " · code `" + e.code() + "`" : "").append(" |\n");
        md.append("| Handler | `").append(e.controller().getSimpleName()).append('.').append(e.method().getName()).append("` |\n\n");

        List<Param> pathParams = e.params().stream().filter(p -> p.in().equals("path")).toList();
        List<Param> query = e.params().stream().filter(p -> p.in().equals("query")).toList();
        List<Param> headers = e.params().stream().filter(p -> p.in().equals("header")).toList();
        List<Param> form = e.params().stream().filter(p -> p.in().equals("form")).toList();
        if (!pathParams.isEmpty() || !query.isEmpty() || !headers.isEmpty() || !form.isEmpty()) {
            md.append("### Parameters\n\n| Name | In | Type | Required | Default | Rules | Sample |\n|---|---|---|---|---|---|---|\n");
            for (Param p : e.params()) {
                md.append("| `").append(p.name()).append("` | ").append(p.in()).append(" | ").append(typeLabel(p.type())).append(" | ")
                        .append(p.required() ? "yes" : "no").append(" | ").append(p.defaultValue() == null ? "" : "`" + p.defaultValue() + "`").append(" | ")
                        .append(p.rules() == null ? "" : p.rules()).append(" | `").append(cell(paramValue(p))).append("` |\n");
            }
            md.append("\n");
        }

        JsonNode body = null;
        if (e.bodyType() != null) {
            body = requestBody(e.bodyType());
            List<String[]> rows = new ArrayList<>();
            fieldRows(e.bodyType(), "", rows, 0, new LinkedHashSet<>());
            if (!rows.isEmpty()) {
                md.append("### Request body fields\n\n| Field | Type | Required | Rules |\n|---|---|---|---|\n");
                for (String[] r : rows) {
                    md.append("| `").append(r[0]).append("` | ").append(r[1]).append(" | ").append(r[2]).append(" | ").append(r[3]).append(" |\n");
                }
                md.append("\n");
            }
        }

        md.append("### Request\n\n```bash\n").append(curl(e, body)).append("\n```\n\n");

        md.append("### Response `").append(e.status()).append("`\n\n");
        if (e.fileType() != null) {
            md.append("A file, not JSON.\n\n```http\nHTTP/1.1 200 OK\nContent-Type: ").append(e.fileType())
                    .append("\nContent-Disposition: attachment; filename=\"").append(fileName(e)).append("\"\n\n<file bytes>\n```\n\n");
        } else {
            JsonNode resp = responseBody(e);
            if (resp == null) {
                md.append("```http\nHTTP/1.1 ").append(e.status()).append(e.status() == 204 ? " No Content" : " OK").append("\n```\n\n");
            } else {
                md.append("```json\n").append(json.writeValueAsString(resp)).append("\n```\n\n");
            }
        }

        md.append("### Error responses\n\n| HTTP | `code` | When |\n|---|---|---|\n");
        if (e.bodyType() != null || !e.params().isEmpty()) {
            md.append("| 400 | `VALIDATION_ERROR` | a required field / parameter is missing or breaks a rule above |\n");
            md.append("| 400 | `INVALID_ARGUMENT` | a value cannot be read (bad UUID, date, enum value) |\n");
        }
        if (e.authenticated()) {
            md.append("| 401 | `UNAUTHORIZED` | no token, or it expired |\n");
        }
        if (e.permission() != null) {
            md.append("| 403 | `FORBIDDEN` | the user lacks: `").append(cell(e.permission())).append("` |\n");
        }
        if (e.tenant() && e.authenticated()) {
            md.append("| 403 | `TENANT_ACCESS_DENIED` | the user does not belong to the school in `X-Tenant-ID` |\n");
        }
        if (!pathParams.isEmpty()) {
            md.append("| 404 | `RESOURCE_NOT_FOUND` | no record with that id |\n");
        }
        if (!e.http().equals("GET")) {
            md.append("| 409 | `DUPLICATE_RESOURCE` / `CONCURRENT_MODIFICATION` | already exists / changed by someone else meanwhile (reload and retry) |\n");
        }
        md.append("| 422 | module code | a business rule refused it: see [business errors](README.md#business-errors-http-422) |\n\n");
        md.append("<details><summary>Error body examples</summary>\n\n").append(errorJson(e)).append("</details>\n\n---\n");
        return md.toString();
    }

    String errorJson(Endpoint e) throws Exception {
        List<String> out = new ArrayList<>();
        if (e.bodyType() != null || !e.params().isEmpty()) {
            out.add(error("VALIDATION_ERROR", "Request validation failed"));
        }
        if (e.authenticated()) {
            out.add(error("UNAUTHORIZED", "Authentication required"));
        }
        if (e.permission() != null) {
            out.add(error("FORBIDDEN", "Access denied"));
        }
        if (e.params().stream().anyMatch(p -> p.in().equals("path"))) {
            out.add(error("RESOURCE_NOT_FOUND", humanize(e.controller().getSimpleName().replace("Controller", "")) + " not found with id: "
                    + uuid("id", e.controller(), 0)));
        }
        return String.join("", out);
    }

    String error(String code, String message) throws Exception {
        int http = switch (code) {
            case "VALIDATION_ERROR" -> 400;
            case "UNAUTHORIZED" -> 401;
            case "FORBIDDEN" -> 403;
            default -> 404;
        };
        return "HTTP " + http + "\n\n```json\n" + errorBody(code, message) + "\n```\n\n";
    }

    String errorBody(String code, String message) throws Exception {
        ObjectNode o = nf.objectNode();
        o.put("success", false);
        o.put("code", code);
        o.put("message", message);
        o.put("timestamp", "2026-11-22T04:30:00Z");
        o.put("correlationId", "c0a80123-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
        return json.writeValueAsString(o);
    }

    JsonNode requestBody(Type t) {
        Class<?> c = raw(t);
        if (c == String.class) {
            ObjectNode o = nf.objectNode();
            o.put("event", "raw body as sent by the caller");
            return o;
        }
        if (c == byte[].class) {
            ObjectNode o = nf.objectNode();
            o.put("event", "raw bytes as sent by the gateway (the signature is computed over them)");
            return o;
        }
        return sample(t, "body", null, 0, 0, new LinkedHashSet<>());
    }

    void fieldRows(Type t, String prefix, List<String[]> rows, int depth, Set<Class<?>> stack) {
        Class<?> c = raw(t);
        if (Collection.class.isAssignableFrom(c)) {
            fieldRows(arg(t, 0), prefix + "[]", rows, depth, stack);
            return;
        }
        if (isSimple(c) || depth > 3 || c.getName().startsWith("java.") || stack.contains(c) || c.isInterface()) {
            return;
        }
        stack.add(c);
        Map<TypeVariable<?>, Type> vars = new HashMap<>();
        TypeVariable<?>[] tv = c.getTypeParameters();
        for (int i = 0; i < tv.length; i++) {
            vars.put(tv[i], arg(t, i));
        }
        for (Prop p : props(c, vars)) {
            String path = prefix.isEmpty() ? p.name() : prefix + (prefix.endsWith("[]") ? "." : ".") + p.name();
            rows.add(new String[]{path, typeLabel(p.type()), p.required() ? "yes" : "no", p.rules()});
            fieldRows(p.type(), path, rows, depth + 1, stack);
        }
        stack.remove(c);
    }

    String paramValue(Param p) {
        if (p.defaultValue() != null && !p.defaultValue().isBlank() && !p.name().equals("sort")) {
            return p.defaultValue();
        }
        if (p.name().equals("sort")) {
            return "createdAt,desc";
        }
        Class<?> c = raw(p.type());
        if (MultipartFile.class.isAssignableFrom(c) || isMultipartList(p.type())) {
            return "@" + fileSample(p.name());
        }
        JsonNode v = sample(p.type(), p.name(), null, 0, 0, new LinkedHashSet<>());
        if (v.isArray()) {
            return v.size() == 0 ? "" : v.get(0).asText();
        }
        if (v.isObject()) {
            try {
                return json.writeValueAsString(v).replaceAll("\\s+", " ");
            } catch (Exception ex) {
                return "{}";
            }
        }
        return v.asText();
    }

    static String fileSample(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("photo") || n.contains("image") || n.contains("logo") || n.contains("avatar")) {
            return "photo.jpg";
        }
        if (n.contains("csv")) {
            return "import.csv";
        }
        return n.contains("file") || n.contains("upload") || n.contains("sheet") ? "import.xlsx" : "document.pdf";
    }

    final Map<Method, String> fileNames = new HashMap<>();

    String fileName(Endpoint e) {
        if (fileNames.containsKey(e.method())) {
            return fileNames.get(e.method());
        }
        String ext = e.fileType().contains("csv") ? "csv" : e.fileType().contains("pdf") ? "pdf" : e.fileType().contains("sheet") ? "xlsx"
                : e.fileType().contains("png") ? "png" : "bin";
        return e.method().getName().replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT) + "." + ext;
    }

    String curl(Endpoint e, JsonNode body) throws Exception {
        String url = e.path();
        for (Param p : e.params()) {
            if (p.in().equals("path")) {
                url = url.replaceAll("\\{" + java.util.regex.Pattern.quote(p.name()) + "(:[^}]*)?}", paramValue(p));
            }
        }
        List<String> q = new ArrayList<>();
        for (Param p : e.params()) {
            if (p.in().equals("query")) {
                q.add(p.name() + "=" + URLEncoder.encode(paramValue(p), StandardCharsets.UTF_8).replace("%2C", ","));
            }
        }
        StringBuilder sb = new StringBuilder("curl -X ").append(e.http()).append(" \"").append(BASE).append(url)
                .append(q.isEmpty() ? "" : "?" + String.join("&", q)).append("\"");
        if (e.authenticated()) {
            sb.append(" \\\n  -H \"Authorization: Bearer $TOKEN\"");
        }
        if (e.tenant()) {
            sb.append(" \\\n  -H \"X-Tenant-ID: ").append(TENANT).append("\"");
        }
        for (Param p : e.params()) {
            if (p.in().equals("header")) {
                sb.append(" \\\n  -H \"").append(p.name()).append(": ").append(headerSample(p.name())).append("\"");
            }
        }
        sb.append(" \\\n  -H \"Accept: application/json\"");
        if (e.multipart()) {
            for (Param p : e.params()) {
                if (p.in().equals("form")) {
                    Class<?> c = raw(p.type());
                    if (MultipartFile.class.isAssignableFrom(c) || isMultipartList(p.type())) {
                        sb.append(" \\\n  -F \"").append(p.name()).append("=@").append(fileSample(p.name())).append("\"");
                    } else {
                        String v = json.writeValueAsString(sample(p.type(), p.name(), null, 0, 0, new LinkedHashSet<>())).replaceAll("\\s*\\n\\s*", " ");
                        sb.append(" \\\n  -F '").append(p.name()).append("=").append(v.replace("'", "'\\''")).append(";type=application/json'");
                    }
                }
            }
        } else if (body != null) {
            sb.append(" \\\n  -H \"Content-Type: application/json\" \\\n  -d '").append(json.writeValueAsString(body).replace("'", "'\\''")).append("'");
        }
        if (e.fileType() != null) {
            sb.append(" \\\n  -o ").append(fileName(e));
        }
        return sb.toString();
    }

    static String headerSample(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("razorpay")) {
            return "5d41402abc4b2a76b9719d911017c592e8f3a1b2c3d4e5f60718293a4b5c6d7e";
        }
        if (n.contains("timestamp")) {
            return "1794300000";
        }
        if (n.contains("signature")) {
            return "q0b6bS0VZ7r0lV2sZQ0k3m2h7nX8qkVY4oQ2b1o7f5c=";
        }
        if (n.contains("user-agent")) {
            return "Mozilla/5.0";
        }
        if (n.contains("idempotency")) {
            return "6f1c0d2e-3b4a-4c5d-9e8f-7a6b5c4d3e2f";
        }
        return "sample";
    }

    static String cell(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }

    String groupMd(String group, List<Controller> controllers, Map<String, String> errors) {
        StringBuilder md = new StringBuilder("# ").append(GROUPS.getOrDefault(group, humanize(group))).append("\n\n");
        long count = controllers.stream().mapToLong(c -> c.endpoints().size()).sum();
        md.append(count).append(" endpoints in ").append(controllers.size()).append(" file(s). Every endpoint has a complete curl request, request body ")
                .append("(with field rules), success response and error responses. Common rules: [`../00_COMMON.md`](../00_COMMON.md). ")
                .append("Sample page: `src/main/resources/templates/samples/").append(group).append(".html`.\n\n");
        for (Controller c : controllers) {
            md.append("## [").append(c.title()).append("](").append(c.type().getSimpleName()).append(".md)\n\n");
            if (!c.description().isBlank()) {
                md.append(c.description()).append("\n\n");
            }
            md.append("| Method | Path | What |\n|---|---|---|\n");
            for (Endpoint e : c.endpoints()) {
                md.append("| `").append(e.http()).append("` | [`").append(e.path()).append("`](").append(c.type().getSimpleName()).append(".md#")
                        .append(e.anchor()).append(") | ").append(cell(e.summary().isBlank() ? humanize(e.method().getName()) : e.summary())).append(" |\n");
            }
            md.append("\n");
        }
        md.append("## Business errors (HTTP 422)\n\n");
        if (errors.isEmpty()) {
            md.append("None specific to this module; see `../00_COMMON.md`.\n");
        } else {
            md.append("Body: `{ \"success\": false, \"code\": \"<CODE>\", \"message\": \"<text>\", \"timestamp\": \"2026-11-22T04:30:00Z\", \"correlationId\": \"c0a80123-4e5f-4a6b-8c7d-9e0f1a2b3c4d\" }`. ")
                    .append("The message below is the start of the text (the rest is filled in with the record's details).\n\n| code | message |\n|---|---|\n");
            errors.forEach((k, v) -> md.append("| `").append(k).append("` | ").append(v).append(" |\n"));
        }
        return md.toString();
    }

    String indexMd(Map<String, List<Controller>> byGroup) {
        long total = byGroup.values().stream().flatMap(List::stream).mapToLong(c -> c.endpoints().size()).sum();
        StringBuilder md = new StringBuilder("# API samples: every endpoint\n\n");
        md.append("Generated from the backend code by `src/test/java/com/vidya/docs/ApiSampleGenerator.java` (").append(total)
                .append(" endpoints). Regenerate after API changes:\n\n```bash\n./mvnw -o test -Dtest=ApiSampleGeneratorTest -Dvidya.samples.out=<this folder>\n```\n\n")
                .append("Start with [`00_COMMON.md`](00_COMMON.md) (login, headers, envelope, paging, errors). HTML sample pages: ")
                .append("`src/main/resources/templates/samples/index.html`. Hand-written walkthroughs with consistent numbers: `../dashboard/`, `../fees/`.\n\n")
                .append("| Module | Folder | Files | Endpoints |\n|---|---|---|---|\n");
        byGroup.forEach((g, cs) -> md.append("| ").append(GROUPS.getOrDefault(g, humanize(g))).append(" | [`").append(g).append("/`](").append(g)
                .append("/README.md) | ").append(cs.size()).append(" | ").append(cs.stream().mapToLong(c -> c.endpoints().size()).sum()).append(" |\n"));
        return md.toString();
    }

    String commonMd() {
        return """
                # Common rules for every endpoint

                ## 1. Log in

                ```bash
                curl -X POST "http://localhost:8080/api/v1/auth/login" \\
                  -H "Content-Type: application/json" \\
                  -H "Accept: application/json" \\
                  -d '{
                  "email": "admin@vgs.edu.in",
                  "password": "Str0ng!Pass2026"
                }'
                ```

                The response's `data.accessToken` is the bearer token (full sample: [`security/AuthController.md`](security/AuthController.md)).
                Keep it in a shell variable for the samples:

                ```bash
                export TOKEN="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.c2FtcGxlLXNpZ25hdHVyZQ"
                ```

                ## 2. Headers

                | Header | When | Value |
                |---|---|---|
                | `Authorization` | every endpoint except login / refresh / logout and gateway webhooks | `Bearer <accessToken>` |
                | `X-Tenant-ID` | school endpoints (not `/auth`, `/schools`, `/onboarding`, `/communities`, `/subscription-plans`, `/payments`, `/admin`, `/platform`, `/webhooks`) | the school's tenant code, e.g. `vgs`. Optional when the token already names one school |
                | `Content-Type` | requests with a JSON body | `application/json` (`multipart/form-data` for uploads) |
                | `Accept` | all | `application/json` |
                | `X-Correlation-ID` | optional | your request id; echoed in errors as `correlationId` |

                ## 3. Success envelope

                ```json
                {
                  "success" : true,
                  "code" : "STUDENT_FOUND",
                  "message" : "Student found",
                  "data" : { },
                  "timestamp" : "2026-11-22T04:30:00Z"
                }
                ```

                `data` is left out when there is nothing to return. Some lists use the paged envelope:

                ```json
                {
                  "success" : true,
                  "code" : "STUDENTS_FOUND",
                  "message" : "Students found",
                  "data" : [ ],
                  "page" : { "number" : 0, "size" : 20, "totalElements" : 2, "totalPages" : 1, "first" : true, "last" : true },
                  "timestamp" : "2026-11-22T04:30:00Z"
                }
                ```

                Others return a Spring `Page` inside `data`:

                ```json
                {
                  "content" : [ ],
                  "pageable" : { "pageNumber" : 0, "pageSize" : 20, "sort" : { "empty" : true, "sorted" : false, "unsorted" : true }, "offset" : 0, "paged" : true, "unpaged" : false },
                  "last" : true, "totalElements" : 2, "totalPages" : 1, "size" : 20, "number" : 0,
                  "sort" : { "empty" : true, "sorted" : false, "unsorted" : true }, "numberOfElements" : 2, "first" : true, "empty" : false
                }
                ```

                Paging parameters: `page` (0-based), `size` (usually max 200), `sort=field,asc|desc`.

                ## 4. Values

                | Kind | Format | Example |
                |---|---|---|
                | ids | UUID | `3f2b8c1e-5d4a-4b6c-9e7f-1a2b3c4d5e6f` |
                | dates | `YYYY-MM-DD` | `2026-11-22` |
                | instants | ISO-8601 UTC | `2026-11-22T04:30:00Z` |
                | money in fees | integer paise (₹1 = 100) | `250000` = ₹2,500.00 |
                | money in set-up (fee structures) | rupees, decimal | `2500.00` |
                | percent | number | `85.5` |

                Sample values in these files are made up from the field names (same name → same value), so ids repeat consistently across calls.

                ## 5. Errors

                ```json
                {
                  "success" : false,
                  "code" : "VALIDATION_ERROR",
                  "message" : "Request validation failed",
                  "timestamp" : "2026-11-22T04:30:00Z",
                  "correlationId" : "c0a80123-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
                }
                ```

                | HTTP | code | Meaning |
                |---|---|---|
                | 400 | `VALIDATION_ERROR` | body / parameter fails a rule (the field-level details are not returned today) |
                | 400 | `INVALID_ARGUMENT` | a value cannot be used (bad UUID / date / enum) |
                | 401 | `UNAUTHORIZED` | no token or expired: refresh with `POST /api/v1/auth/refresh` |
                | 403 | `FORBIDDEN` | missing permission |
                | 403 | `TENANT_ACCESS_DENIED`, `TENANT_SUSPENDED` | not your school / school suspended |
                | 404 | `RESOURCE_NOT_FOUND`, `TENANT_NOT_FOUND` | unknown id / school |
                | 409 | `DUPLICATE_RESOURCE` | unique value already used |
                | 409 | `CONCURRENT_MODIFICATION` | someone changed the record meanwhile: reload and retry |
                | 422 | module-specific (e.g. `FEE_OVERPAYMENT`) | a business rule refused the request: each module's `README.md` lists them |
                | 422 | `INVALID_STATE_TRANSITION` | the record is not in a state that allows this |
                | 500 | `INTERNAL_ERROR` | unexpected; quote the `correlationId` |
                """;
    }

    // ── HTML sample pages ────────────────────────────────────────────────────────────

    String groupHtml(String group, List<Controller> controllers, Set<String> groups) {
        StringBuilder h = new StringBuilder();
        String title = GROUPS.getOrDefault(group, humanize(group));
        h.append("<!DOCTYPE html>\n<!--\n  Sample screen for: ").append(title).append("\n  Plain HTML + CSS with static sample data generated from the API")
                .append(" (APiReqDoc/samples/api/").append(group).append("/).\n  Each section names the endpoints that feed it. Rebuild it in the frontend")
                .append(" framework; this file is not served by any controller.\n-->\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n<title>").append(esc(title))
                .append("</title>\n<link rel=\"stylesheet\" href=\"samples.css\">\n</head>\n<body>\n");
        h.append("<header class=\"topbar\"><a class=\"brand\" href=\"index.html\">Vidya</a><h1>").append(esc(title)).append("</h1></header>\n");
        h.append("<div class=\"layout\">\n<nav class=\"side\">\n");
        for (String g : groups) {
            h.append("<a href=\"").append(g).append(".html\"").append(g.equals(group) ? " class=\"on\"" : "").append(">")
                    .append(esc(GROUPS.getOrDefault(g, humanize(g)))).append("</a>\n");
        }
        h.append("</nav>\n<main>\n");
        if (controllers.size() > 1) {
            h.append("<div class=\"tabs\">");
            for (Controller c : controllers) {
                h.append("<a href=\"#").append(c.type().getSimpleName()).append("\">").append(esc(c.title())).append("</a>");
            }
            h.append("</div>\n");
        }
        for (Controller c : controllers) {
            h.append(controllerHtml(c, group));
        }
        h.append("</main>\n</div>\n</body>\n</html>\n");
        return h.toString();
    }

    String controllerHtml(Controller c, String group) {
        StringBuilder h = new StringBuilder();
        h.append("\n<!-- ").append(c.type().getSimpleName()).append(" · samples/api/").append(group).append("/").append(c.type().getSimpleName())
                .append(".md -->\n<section class=\"card\" id=\"").append(c.type().getSimpleName()).append("\">\n<h2>").append(esc(c.title())).append("</h2>\n");
        if (!c.description().isBlank()) {
            h.append("<p class=\"muted\">").append(esc(c.description())).append("</p>\n");
        }
        Endpoint list = c.endpoints().stream().filter(e -> e.http().equals("GET") && listElement(e) != null)
                .min(Comparator.comparingInt((Endpoint e) -> (int) e.params().stream().filter(p -> p.in().equals("path")).count())
                        .thenComparingInt(e -> e.path().length()))
                .orElse(null);
        Endpoint create = c.endpoints().stream().filter(e -> e.http().equals("POST") && e.bodyType() != null && !raw(e.bodyType()).isArray()
                        && !isSimple(raw(e.bodyType())) && raw(e.bodyType()) != String.class)
                .min(Comparator.comparingInt(e -> e.path().length())).orElse(null);
        Endpoint detail = c.endpoints().stream().filter(e -> e.http().equals("GET") && e.params().stream().anyMatch(p -> p.in().equals("path"))
                        && listElement(e) == null && dataType(e) != null && !isSimple(raw(dataType(e))))
                .min(Comparator.comparingInt(e -> e.path().length())).orElse(null);

        if (list != null) {
            List<Param> filters = list.params().stream().filter(p -> p.in().equals("query") && !Set.of("page", "size", "sort").contains(p.name())).toList();
            h.append("<!-- ").append(list.http()).append(" ").append(list.path()).append(" -->\n");
            if (!filters.isEmpty()) {
                h.append("<form class=\"filters\">\n");
                for (Param p : filters.stream().limit(8).toList()) {
                    h.append(input(p.name(), p.type(), false, paramValue(p)));
                }
                h.append("<button type=\"button\" class=\"btn primary\">Apply</button>\n</form>\n");
            }
            Type el = listElement(list);
            Class<?> elc = raw(el);
            List<Prop> cols = isSimple(elc) ? List.of() : props(elc, Map.of()).stream().filter(p -> isSimple(raw(p.type()))
                    && !(raw(p.type()) == UUID.class)).limit(8).toList();
            h.append("<div class=\"scroll\"><table>\n<thead><tr>");
            if (cols.isEmpty()) {
                h.append("<th>Value</th>");
            }
            for (Prop p : cols) {
                h.append("<th>").append(esc(humanize(p.name()))).append("</th>");
            }
            h.append("<th></th></tr></thead>\n<tbody>\n");
            for (int v = 0; v < 3; v++) {
                JsonNode row = sample(el, "item", c.type(), v, 1, new LinkedHashSet<>());
                h.append("<tr>");
                if (cols.isEmpty()) {
                    h.append("<td>").append(esc(row.asText())).append("</td>");
                }
                for (Prop p : cols) {
                    h.append("<td>").append(cellHtml(p, row.get(p.name()))).append("</td>");
                }
                h.append("<td class=\"actions\"><a href=\"#\">Open</a></td></tr>\n");
            }
            h.append("</tbody>\n</table></div>\n<p class=\"muted small\">Source: <code>").append(list.http()).append(" ").append(esc(list.path()))
                    .append("</code></p>\n");
        }
        if (detail != null) {
            Type dt = dataType(detail);
            JsonNode d = sample(dt, "data", c.type(), 0, 1, new LinkedHashSet<>());
            h.append("<!-- ").append(detail.http()).append(" ").append(detail.path()).append(" -->\n<h3>Details</h3>\n<dl class=\"details\">\n");
            int shown = 0;
            for (Prop p : props(raw(dt), Map.of())) {
                if (!isSimple(raw(p.type())) || raw(p.type()) == UUID.class || shown++ >= 12) {
                    continue;
                }
                h.append("<div><dt>").append(esc(humanize(p.name()))).append("</dt><dd>").append(cellHtml(p, d.get(p.name()))).append("</dd></div>\n");
            }
            h.append("</dl>\n<p class=\"muted small\">Source: <code>").append(detail.http()).append(" ").append(esc(detail.path())).append("</code></p>\n");
        }
        if (create != null) {
            h.append("<!-- ").append(create.http()).append(" ").append(create.path()).append(" -->\n<details class=\"form\"><summary>")
                    .append(esc(create.summary().isBlank() ? humanize(create.method().getName()) : create.summary())).append("</summary>\n<form>\n");
            int shown = 0;
            for (Prop p : props(raw(create.bodyType()), Map.of())) {
                if (shown++ >= 16) {
                    break;
                }
                Class<?> pc = raw(p.type());
                if (isSimple(pc)) {
                    JsonNode v = sample(p.type(), p.name(), raw(create.bodyType()), 0, 1, new LinkedHashSet<>());
                    h.append(input(p.name(), p.type(), p.required(), v.asText()));
                } else {
                    h.append("<label class=\"wide\">").append(esc(humanize(p.name()))).append(p.required() ? " *" : "")
                            .append("<span class=\"muted small\">").append(esc(typeLabel(p.type()).replace("`", ""))).append(" (see the API file)</span></label>\n");
                }
            }
            h.append("<div class=\"wide\"><button type=\"button\" class=\"btn primary\">Save</button> <button type=\"reset\" class=\"btn\">Reset</button></div>\n")
                    .append("</form>\n</details>\n");
        }
        h.append("<details class=\"endpoints\"><summary>All endpoints of this section (").append(c.endpoints().size()).append(")</summary>\n<table>\n");
        for (Endpoint e : c.endpoints()) {
            h.append("<tr><td><span class=\"verb ").append(e.http().toLowerCase(Locale.ROOT)).append("\">").append(e.http()).append("</span></td><td><code>")
                    .append(esc(e.path())).append("</code></td><td>").append(esc(e.summary().isBlank() ? humanize(e.method().getName()) : e.summary()))
                    .append("</td></tr>\n");
        }
        h.append("</table>\n</details>\n</section>\n");
        return h.toString();
    }

    Type dataType(Endpoint e) {
        Type t = unwrapEntity(e.returnType());
        Class<?> c = raw(t);
        if (c == ApiResponse.class) {
            return arg(t, 0);
        }
        return c == void.class || c == Void.class || c == PagedApiResponse.class ? null : t;
    }

    Type listElement(Endpoint e) {
        Type t = unwrapEntity(e.returnType());
        Class<?> c = raw(t);
        if (c == PagedApiResponse.class) {
            return arg(t, 0);
        }
        if (c == ApiResponse.class) {
            Type d = arg(t, 0);
            Class<?> dc = raw(d);
            if (Page.class.isAssignableFrom(dc) || List.class.isAssignableFrom(dc) || Set.class.isAssignableFrom(dc)) {
                return arg(d, 0);
            }
        }
        return null;
    }

    String input(String name, Type type, boolean required, String value) {
        Class<?> c = raw(type);
        String label = esc(humanize(name)) + (required ? " *" : "");
        if (c.isEnum()) {
            StringBuilder s = new StringBuilder("<label>").append(label).append("<select name=\"").append(name).append("\">");
            if (!required) {
                s.append("<option value=\"\">Any</option>");
            }
            for (Object o : c.getEnumConstants()) {
                s.append("<option>").append(((Enum<?>) o).name()).append("</option>");
            }
            return s.append("</select></label>\n").toString();
        }
        if (c == boolean.class || c == Boolean.class) {
            return "<label class=\"check\"><input type=\"checkbox\" name=\"" + name + "\" checked> " + label + "</label>\n";
        }
        String kind = c == LocalDate.class ? "date" : c == LocalTime.class ? "time" : c == YearMonth.class ? "month"
                : Number.class.isAssignableFrom(c) || (c.isPrimitive() && c != char.class) ? "number" : "text";
        String n = name.toLowerCase(Locale.ROOT);
        if (kind.equals("text") && (n.contains("description") || n.contains("remark") || n.contains("reason") || n.contains("note")
                || n.contains("content") || n.contains("body"))) {
            return "<label class=\"wide\">" + label + "<textarea name=\"" + name + "\" rows=\"2\">" + esc(value) + "</textarea></label>\n";
        }
        if (kind.equals("text") && n.contains("email")) {
            kind = "email";
        }
        if (kind.equals("text") && (n.contains("phone") || n.contains("mobile"))) {
            kind = "tel";
        }
        return "<label>" + label + "<input type=\"" + kind + "\" name=\"" + name + "\" value=\"" + esc(value) + "\"" + (required ? " required" : "")
                + "></label>\n";
    }

    String cellHtml(Prop p, JsonNode v) {
        if (v == null || v.isNull()) {
            return "<span class=\"muted\">–</span>";
        }
        String n = p.name().toLowerCase(Locale.ROOT);
        if (n.contains("paise") && v.isNumber()) {
            return "₹" + String.format(Locale.ROOT, "%,.2f", v.asLong() / 100.0);
        }
        if (v.isBoolean()) {
            return v.asBoolean() ? "<span class=\"pill ok\">Yes</span>" : "<span class=\"pill\">No</span>";
        }
        if (raw(p.type()).isEnum() || n.equals("status")) {
            String s = v.asText();
            String cls = s.matches("ACTIVE|PAID|SUCCESS|APPROVED|PUBLISHED|PRESENT|DONE|COMPLETED|CLEARED") ? "ok"
                    : s.matches("PENDING.*|DRAFT|PARTIAL|SUBMITTED|SCHEDULED|UPCOMING") ? "warn"
                    : s.matches("INACTIVE|FAILED|REJECTED|OVERDUE|ABSENT|CANCELLED|BOUNCED|SUSPENDED|EXPIRED") ? "bad" : "";
            return "<span class=\"pill " + cls + "\">" + esc(s) + "</span>";
        }
        return esc(v.asText());
    }

    String indexHtml(Map<String, List<Controller>> byGroup) {
        StringBuilder h = new StringBuilder("<!DOCTYPE html>\n<!-- Index of the generated sample screens (one per module). -->\n<html lang=\"en\">\n<head>\n")
                .append("<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n<title>Vidya Sample Screens</title>\n")
                .append("<link rel=\"stylesheet\" href=\"samples.css\">\n</head>\n<body>\n<header class=\"topbar\"><a class=\"brand\" href=\"index.html\">Vidya</a>")
                .append("<h1>Sample screens</h1></header>\n<main class=\"solo\">\n<p class=\"muted\">One page per module, built from the API samples. ")
                .append("The hand-made overview dashboard is <a href=\"../dashboard-sample.html\">dashboard-sample.html</a>.</p>\n<div class=\"grid\">\n");
        byGroup.forEach((g, cs) -> {
            h.append("<a class=\"card link\" href=\"").append(g).append(".html\"><h2>").append(esc(GROUPS.getOrDefault(g, humanize(g)))).append("</h2><p class=\"muted\">");
            h.append(esc(cs.stream().map(Controller::title).collect(Collectors.joining(" · ")))).append("</p><p class=\"small\">")
                    .append(cs.stream().mapToLong(c -> c.endpoints().size()).sum()).append(" endpoints</p></a>\n");
        });
        return h.append("</div>\n</main>\n</body>\n</html>\n").toString();
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static final String CSS = """
            /* Shared look of the generated sample screens. Plain CSS: replace with the frontend's design system. */
            :root {
              --bg: #f5f6f8; --card: #fff; --text: #1d2330; --muted: #687083; --line: #e3e6ec; --brand: #2f5bea;
              --ok: #1a8f5a; --ok-bg: #e5f5ec; --warn: #a86400; --warn-bg: #fdf1dc; --bad: #c33a32; --bad-bg: #fbe7e5; --grey-bg: #eef0f3;
            }
            * { box-sizing: border-box; }
            body { margin: 0; background: var(--bg); color: var(--text); font: 14px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
            a { color: var(--brand); text-decoration: none; }
            code { font-family: ui-monospace, Menlo, monospace; font-size: 12px; }
            .topbar { display: flex; gap: 16px; align-items: center; padding: 12px 24px; background: var(--card); border-bottom: 1px solid var(--line); }
            .topbar h1 { margin: 0; font-size: 17px; }
            .brand { font-weight: 800; color: var(--text); }
            .layout { display: grid; grid-template-columns: 240px 1fr; min-height: calc(100vh - 50px); }
            .side { background: var(--card); border-right: 1px solid var(--line); padding: 12px 0; }
            .side a { display: block; padding: 7px 20px; color: var(--muted); }
            .side a.on, .side a:hover { color: var(--brand); background: var(--grey-bg); }
            main { padding: 20px 24px 40px; display: grid; gap: 16px; align-content: start; min-width: 0; }
            main.solo { max-width: 1100px; margin: 0 auto; }
            .tabs { display: flex; flex-wrap: wrap; gap: 6px; }
            .tabs a { padding: 6px 12px; border: 1px solid var(--line); border-radius: 99px; background: var(--card); }
            .card { background: var(--card); border: 1px solid var(--line); border-radius: 10px; padding: 16px; min-width: 0; }
            .card h2 { margin: 0 0 8px; font-size: 16px; }
            .card h3 { margin: 16px 0 8px; font-size: 14px; }
            .card.link:hover { border-color: var(--brand); }
            .grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
            .muted { color: var(--muted); }
            .small { font-size: 12px; }
            .filters { display: flex; flex-wrap: wrap; gap: 10px; align-items: flex-end; margin: 8px 0 12px; }
            label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--muted); }
            label.check { flex-direction: row; align-items: center; }
            input, select, textarea { font: inherit; color: var(--text); padding: 6px 8px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); min-width: 140px; }
            .btn { font: inherit; padding: 7px 14px; border: 1px solid var(--line); border-radius: 6px; background: var(--card); cursor: pointer; }
            .btn.primary { background: var(--brand); border-color: var(--brand); color: #fff; }
            .scroll { overflow-x: auto; }
            table { width: 100%; border-collapse: collapse; }
            th, td { padding: 8px; border-bottom: 1px solid var(--line); text-align: left; white-space: nowrap; }
            th { font-size: 12px; color: var(--muted); font-weight: 600; }
            .actions { text-align: right; }
            .pill { display: inline-block; padding: 1px 8px; border-radius: 99px; font-size: 11px; font-weight: 700; background: var(--grey-bg); color: var(--muted); }
            .pill.ok { background: var(--ok-bg); color: var(--ok); }
            .pill.warn { background: var(--warn-bg); color: var(--warn); }
            .pill.bad { background: var(--bad-bg); color: var(--bad); }
            .details { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 10px 16px; margin: 0; }
            .details dt { font-size: 12px; color: var(--muted); }
            .details dd { margin: 0; font-weight: 600; overflow-wrap: anywhere; }
            details.form, details.endpoints { margin-top: 14px; border-top: 1px solid var(--line); padding-top: 10px; }
            details summary { cursor: pointer; font-weight: 600; }
            details.form form { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; margin-top: 12px; }
            .wide { grid-column: 1 / -1; }
            .verb { font: 700 11px ui-monospace, Menlo, monospace; padding: 2px 6px; border-radius: 4px; background: var(--grey-bg); }
            .verb.get { color: var(--ok); } .verb.post { color: var(--brand); } .verb.put, .verb.patch { color: var(--warn); } .verb.delete { color: var(--bad); }
            @media (max-width: 760px) {
              .layout { grid-template-columns: 1fr; }
              .side { display: flex; overflow-x: auto; border-right: 0; border-bottom: 1px solid var(--line); padding: 0; }
              .side a { white-space: nowrap; padding: 10px 14px; }
              main, .topbar { padding-left: 16px; padding-right: 16px; }
            }
            """;
}
