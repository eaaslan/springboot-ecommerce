package com.backendguru.userservice.user.dto;

import com.backendguru.userservice.user.Address;
import com.backendguru.userservice.user.User;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

  @Mapping(target = "addresses", ignore = true)
  UserResponse toResponse(User user);

  UserResponse.AddressDto toAddressDto(Address address);

  @Mapping(target = "addresses", expression = "java(toAddressDtos(user.getAddresses()))")
  UserResponse toResponseWithAddresses(User user);

  default List<UserResponse.AddressDto> toAddressDtos(List<Address> addresses) {
    return addresses == null ? List.of() : addresses.stream().map(this::toAddressDto).toList();
  }
}
