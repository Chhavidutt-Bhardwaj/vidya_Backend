package com.vidya.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class WebPageController {

    /**
     * Serves the main SEO & LLM-optimized Vidya landing page.
     */
    @GetMapping(value = {"/", "/index", "/index.html"})
    public String index(Model model) {
        model.addAttribute("pageTitle", "Vidya | AI-Powered School ERP & FinTech Ecosystem");
        model.addAttribute("metaDescription", "Vidya is an enterprise-grade, multi-tenant School ERP and FinTech platform providing automated fee collection (e-NACH/UPI), student lifecycle management, attendance, and smart parent loan assistance.");
        model.addAttribute("canonicalUrl", "https://vidya.io/");
        return "index";
    }

    /**
     * Serves dynamic tenant school landing portals with institution-specific SEO and Schema.org context.
     */
    @GetMapping("/schools/{slug}")
    public String schoolPortal(@PathVariable("slug") String slug, Model model) {
        // Fallback/demo defaults for dynamic tenant landing
        String formattedName = slug.replace("-", " ");
        formattedName = Character.toUpperCase(formattedName.charAt(0)) + formattedName.substring(1);
        
        model.addAttribute("schoolName", formattedName + " School");
        model.addAttribute("schoolInitial", String.valueOf(formattedName.charAt(0)));
        model.addAttribute("schoolSlug", slug);
        model.addAttribute("schoolBoard", "CBSE & International Curriculum");
        model.addAttribute("schoolDescription", "Official digital campus portal for " + formattedName + " School. Manage admissions, instant online fee payments, and academic updates.");
        model.addAttribute("canonicalUrl", "https://vidya.io/schools/" + slug);
        return "school-portal";
    }

    /**
     * Serves standard llms.txt for AI Search Engines & LLMs (GEO).
     */
    @GetMapping(value = "/llms.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public ResponseEntity<Resource> getLlmsTxt() {
        Resource resource = new ClassPathResource("static/llms.txt");
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    /**
     * Serves in-depth llms-full.txt for deep LLM knowledge context.
     */
    @GetMapping(value = "/llms-full.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public ResponseEntity<Resource> getLlmsFullTxt() {
        Resource resource = new ClassPathResource("static/llms-full.txt");
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    /**
     * Serves robots.txt for search engines and AI crawlers.
     */
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public ResponseEntity<Resource> getRobotsTxt() {
        Resource resource = new ClassPathResource("static/robots.txt");
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    /**
     * Serves sitemap.xml for indexing.
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public ResponseEntity<Resource> getSitemapXml() {
        Resource resource = new ClassPathResource("static/sitemap.xml");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(resource);
    }
}
