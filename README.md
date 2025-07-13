### Microservices Bootstrap project

This project serves as a template to bootstrap a microservices project using Spring Boot.

**Features**...

* JWTFilter and DemoAuthController show the use of JWT token for authentication. All API requires a valid JWT token in HTTP Authorization header as authentication except for /auth/token and any URI starting with /demo.
  JWT token can be retrieved by making HTTP Post request to /auth/token with request data

  * ```{
         "username": "autumnWind",
         "password": "HaGqklkGHL4567"
    ```}

    and HTTP header moduleCode=DEMO

  * Sample successful response

    * ```{
          "outcomeCode": "0",
          "outcomeMessage": "Success",
          "outcomeUserMessage": null,
             "data": {
               "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJEZW1vIEx0ZCIsImp0aSI6ImQ3NDM2NzRhLTVlY2UtNDE2OS1hZDc3LWRhMTY2M2I2OTMzOCIsImlhdCI6MTc1MjM3Mzk2OSwiZXhwIjoxNzUyMzc3NTY5LCJtb2R1bGVDb2RlIjoiREVNTyIsInVzZXJJZCI6Ijc1Njc4MTE3IiwidXNlck5hbWUiOiJhdXR1bW5XaW5kIiwidXNlclR5cGUiOiIzIn0.NjUDlANLo70XaZNhYkP9aS5RPsQSaOjmmUl12ajouWs",
               "userId": null,
               "expiration": "2025-07-13 15:32:49"
             }
         }
      ```
    * JWT token obtained above can be used to access Get Card Details API at HTTP Get /cards/60800838383 where card number is 60800838383
  * Uncomment and configure spring.mail.* in application-uat.yml to send email using EmailService when a third party API fails to respond under conditions

    * 3 failed attempts
    * A cooldown of 1h for each third party API defined in enum ErrorNotifierType
  * Uncomment @Scheduled(...) line of DemoExchangeRateProcessor to extract USD exchange rate from public API of Westpac, once every 10 sec. The extracted rate will then be printed to console.

Created: 14-July-2025

Samuel Huang
