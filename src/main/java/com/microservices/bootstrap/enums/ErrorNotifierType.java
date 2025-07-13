package com.microservices.bootstrap.enums;

/**
 * Define one error type for each process or external API here. Each error type enum will be used in cooldown of sending
 * error notification email. See EmailService for details.
 */
public enum ErrorNotifierType {

   WESTPAC,
   ASB,
   BNZ,

   PROCESS_1,
   PROCESS_2

}
