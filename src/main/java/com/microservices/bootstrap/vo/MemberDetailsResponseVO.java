package com.microservices.bootstrap.vo;

import com.microservices.bootstrap.vo.auth.BaseResponseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class MemberDetailsResponseVO extends BaseResponseVO {

   private Long traderId;

   private String firstName;
   private String middleName;
   private String lastName;

   private String phoneNumber;
   private String email;

   private String physicalAddress1;
   private String physicalAddress2;
   private String mailingAddress1;
   private String mailingAddress2;

   private Integer memberStatusId;

}
