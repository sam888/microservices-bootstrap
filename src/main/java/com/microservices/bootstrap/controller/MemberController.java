package com.microservices.bootstrap.controller;

import com.microservices.bootstrap.service.MemberService;
import com.microservices.bootstrap.vo.ApiResponseVO;
import com.microservices.bootstrap.vo.MemberDetailsResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * @author samuel.huang
 * Created: 5-September-2026
 */
@Slf4j
@RestController
@RequestMapping("/members")
public class MemberController {

   private final MemberService memberService;

   public MemberController(MemberService memberService) {
      this.memberService = memberService;
   }

   @GetMapping("/{traderId}")
   public Mono<ApiResponseVO<MemberDetailsResponseVO>> getMemberDetails(
           @RequestAttribute("moduleCode") String moduleCode,
           @PathVariable Long traderId) {
      return memberService.getMemberDetails(moduleCode, traderId);
   }
}
