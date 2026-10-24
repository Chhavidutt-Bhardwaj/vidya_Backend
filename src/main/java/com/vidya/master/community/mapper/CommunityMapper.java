package com.vidya.master.community.mapper;

import com.vidya.master.community.dto.CommunityMemberResponse;
import com.vidya.master.community.dto.CommunityResponse;
import com.vidya.master.community.entity.Community;
import com.vidya.master.community.entity.CommunityMember;
import org.springframework.stereotype.Component;

/**
 * Maps between Community domain entities and DTO records.
 */
@Component
public class CommunityMapper {

    public CommunityResponse toResponse(Community entity, long memberCount) {
        if (entity == null) {
            return null;
        }
        return new CommunityResponse(
                entity.getId(),
                entity.getCommunityCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getType(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                memberCount
        );
    }

    public CommunityResponse toResponse(Community entity) {
        if (entity == null) {
            return null;
        }
        long count = entity.getMembers() != null ? entity.getMembers().size() : 0;
        return toResponse(entity, count);
    }

    public CommunityMemberResponse toMemberResponse(CommunityMember member) {
        if (member == null) {
            return null;
        }
        return new CommunityMemberResponse(
                member.getId(),
                member.getCommunity().getId(),
                member.getCommunity().getCommunityCode(),
                member.getPlatformUserId(),
                member.getMemberCode(),
                member.getCustomRole() != null ? member.getCustomRole().getId() : null,
                member.getEffectiveRoleName(),
                member.getStatus(),
                member.getJoinedAt(),
                member.getLastActiveAt(),
                member.getCreatedAt()
        );
    }
}
