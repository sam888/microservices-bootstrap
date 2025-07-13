package com.microservices.bootstrap.service;

import com.microservices.bootstrap.enums.ErrorNotifierType;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Service class to send actual email.
 *
 * @author samuel.huang
 * Created: 20-June-2022
 * Last updated: 4-July-2025 
 */
@Slf4j
@Service
@ConditionalOnBean(JavaMailSender.class) // JavaMailSender bean will only be loaded if spring.mail.host is defined.
public class EmailService {

    @Value("${email.sendTo}")
    private String emailSendTo;

    @Value("${email.sendCc}")
    private String emailCc;

    @Value("${email.sendBcc}")
    private String emailBcc;

    // @Value("${spring.mail.username}")
    private final String sendFrom = "administration@demo-demo.com";

    private static final String NOREPLY_ADDRESS = "noreply@demo-demo.com";
    private static final String LINE_SEPARATOR = System.lineSeparator();

    // Cooldown duration for notifications
    private static final Duration COOLDOWN = Duration.ofMinutes( 60 );

    // Tracks last sent time per notifier type
    private static final Map<ErrorNotifierType, AtomicReference<Instant>> lastNotificationSentMap = new EnumMap<>( ErrorNotifierType.class );

    static {
        // Initialize map with Instant.MIN to allow first send immediately
        for (ErrorNotifierType type : ErrorNotifierType.values()) {
            lastNotificationSentMap.put( type, new AtomicReference<>( Instant.MIN ) );
        }
    }
    
     
    private final JavaMailSender javaMailSender;

    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }
    
    @PostConstruct
    protected void intitialize() {
        validateEmails(); // Need to do it here. Put this in constructor will cause exception.
    }
    
    public void sendErrorNotificationEmail(String subject, String message, ErrorNotifierType errorNotifierType, Exception exception) {
        if ( canSendErrorNotificationEmailByRateLimiting( errorNotifierType ) ) {
            resetLastNotificationEmailSentTime( errorNotifierType );
            sendEmailByException( exception, subject, message);
        }
    }

    public void sendEmailByException(Exception ex, String subject, String message) {
       sendEmailByException(ex, subject, message, false, null);
    }    

    public void sendEmailByException(Exception ex, String subject, String message, boolean isTestRun, String testEmail) {
        // Extract first 25 lines of exception stacktrace
        StringWriter stringWriter = new StringWriter();
        ex.printStackTrace( new PrintWriter( stringWriter ) );
        String first25StackTraceLines = stringWriter.toString().lines().limit( 25 ).collect( Collectors.joining( "\n" ) );

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            // pass 'true' to the constructor to create a multipart message
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            setEmailsToSendTo( helper, isTestRun, testEmail );
            helper.setSubject( subject );
            
            String doubleLineSeparator = LINE_SEPARATOR + LINE_SEPARATOR; 
            helper.setText(message + doubleLineSeparator +
               "Exception Message: " + ex.getMessage() + doubleLineSeparator + 
               "Exception Stacktrace: " + first25StackTraceLines );

            String logMessage = "Sending email to report unexpected exception... ";
            log.info( logMessage );
            
            javaMailSender.send( mimeMessage );
            log.info( "Email sent successfully...");
        } catch (MessagingException messagingException) {
            messagingException.printStackTrace();
            log.error("Error sending email to report unexpected exception...", messagingException);
        }
    }


    public void validateEmails() {
        if ( StringUtils.isEmpty( emailSendTo ) ) {
            throw new IllegalStateException("email.sendTo cannot be empty in application.yml or application-{profile}.yml");
        }
        String[] emailSendToArray = getEmailArray( emailSendTo );
        String[] emailCcArray = getEmailArray( emailCc );
        String[] emailBccArray = getEmailArray( emailBcc );

        validateEmails( "Send-To", emailSendToArray);
        validateEmails( "CC", emailCcArray);
        validateEmails( "BCC", emailBccArray);
    }

    public String[] getEmailArray(String emails) {
        String[] emailArray = emails.split( "," );
        return StringUtils.stripAll( emailArray ); // strip away trailing empty space
    }

    public void validateEmails(String emailKind, String[] emailArray) {
        String emailSendToLog = getMultiLinMessage( Arrays.asList( emailArray ));
        log.info(emailKind + " email addresses: " + System.lineSeparator() + emailSendToLog);
        if ( emailArray.length == 0 ) return;

        Arrays.stream( emailArray ).forEach( email -> {
            if ( StringUtils.isEmpty( email ) ) return;
            boolean isValidEmail = EmailValidator.getInstance().isValid( email );
            if ( ! isValidEmail ) {
                throw new IllegalStateException("Invalid email address detected in " + emailKind + " Email: " + email);
            }
        });
    }

    protected boolean canSendErrorNotificationEmailByRateLimiting(ErrorNotifierType errorNotifierType) {
        AtomicReference<Instant> lastSentAtomicReference = lastNotificationSentMap.get( errorNotifierType );
        Instant lastSentTime = lastSentAtomicReference.get();
        Instant now = Instant.now();

        return ( Duration.between( lastSentTime, now ).compareTo( COOLDOWN ) >= 0 );
    }

    private void setEmailsToSendTo(MimeMessageHelper mimeMessageHelper, boolean isTestRun, String testEmail) throws MessagingException {
        mimeMessageHelper.setFrom( sendFrom );
        
        if ( isTestRun ) {
            if ( StringUtils.isNotBlank( testEmail ) ) {
                mimeMessageHelper.setTo( getEmailArray( testEmail ) );
            }
            return;
        }
        
        mimeMessageHelper.setTo( getEmailArray( emailSendTo ) );
        if ( StringUtils.isNotBlank( emailCc )) {
            mimeMessageHelper.setCc( getEmailArray( emailCc ) );
        }

        if ( StringUtils.isNotBlank( emailBcc )) {
            mimeMessageHelper.setBcc( getEmailArray( emailBcc ) );
        }
    }


    private String getMultiLinMessage(List<String> messageList) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        messageList.forEach( printWriter::println );
        return stringWriter.toString();
    }
    
    private void resetLastNotificationEmailSentTime(ErrorNotifierType errorNotifierType) {
        AtomicReference<Instant> lastSentAtomicReference = lastNotificationSentMap.get( errorNotifierType );
        Instant lastSentTime = lastSentAtomicReference.get();
        Instant now = Instant.now();

        lastSentAtomicReference.compareAndSet(lastSentTime, now);
    }
}
