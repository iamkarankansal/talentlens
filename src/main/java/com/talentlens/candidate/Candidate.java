package com.talentlens.candidate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "candidates")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(length = 50000)
    private String resumeText;

    private String headline;

    private Double experienceYears;

    @ElementCollection
    @CollectionTable(name = "candidate_skills", joinColumns = @JoinColumn(name = "candidate_id"))
    @Column(name = "skill", nullable = false)
    private List<String> skills = new ArrayList<>();

    private Instant profileParsedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getResumeText() { return resumeText; }
    public void setResumeText(String resumeText) { this.resumeText = resumeText; }
    public String getHeadline() { return headline; }
    public Double getExperienceYears() { return experienceYears; }
    public List<String> getSkills() { return skills; }
    public Instant getProfileParsedAt() { return profileParsedAt; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Replaces the parsed resume profile. Passing a null parse time clears it.
     */
    public void setProfile(String headline, Double experienceYears, List<String> skills, Instant parsedAt) {
        this.headline = headline;
        this.experienceYears = experienceYears;
        this.skills.clear();
        this.skills.addAll(skills);
        this.profileParsedAt = parsedAt;
    }

    public void clearProfile() {
        setProfile(null, null, List.of(), null);
    }
}
