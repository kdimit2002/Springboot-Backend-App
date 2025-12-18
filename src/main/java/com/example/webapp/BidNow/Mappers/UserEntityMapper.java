package com.example.webapp.BidNow.Mappers;

import com.example.webapp.BidNow.Enums.Avatar;
import com.example.webapp.BidNow.Dtos.UserEntityDto;
import com.example.webapp.BidNow.Entities.Role;
import com.example.webapp.BidNow.Entities.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.HashSet;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface UserEntityMapper {

    @Mapping(target = "role", expression = "java(joinRoles(user.getRoles()))")
    @Mapping(target = "avatarUrl", expression = "java(setAvatarUrl(user.getAvatar()))")
    UserEntityDto toDto(UserEntity user);

    default String joinRoles(Set<Role> roles) {
        Set<String> roleNames = new HashSet<>();
        for(Role r : roles)roleNames.add(r.getName());
        return roleNames.isEmpty() ? "" : String.join(", ", roleNames);
    }


    default String setAvatarUrl(Avatar avatar){return avatar.getUrl();}

}
