package com.microservices.bootstrap.service;

import freemarker.core.Environment;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

/**
 * @author samuel.huang
 * Created: 16/9/2021
 *
 */
@Slf4j
@Service
public class FreeMarkerTemplateService {

    private final FreeMarkerConfigurer freeMarkerConfigurer;

    public FreeMarkerTemplateService(FreeMarkerConfigurer freeMarkerConfigurer) {
        this.freeMarkerConfigurer = freeMarkerConfigurer;
        freeMarkerConfigurer.setDefaultEncoding("UTF-8"); // Default encoding of the template files
    }

    enum ErrorHandlerEnum {

        /**
         * Error here means exception caught in handler while processing the template.
         *
         * IGNORE_ERROR_REMOVE_BAD_EXPRESSION - Ignore exception caught and removes the expression (e.g. ${test}) causing the
         *                                      error in template. Processing will continue to finish regardless of errors.
         * IGNORE_ERROR_KEEP_BAD_EXPRESSION - Ignore exception caught but keeps the expression (e.g. ${test}) causing the error
         *                                    in template. Processing will continue to finish regardless of errors.
         * RETHROW_EXCEPTION_ON_ERROR - Rethrow exception caught and stops processing the template
         */
        IGNORE_ERROR_REMOVE_BAD_EXPRESSION( TemplateExceptionHandler.IGNORE_HANDLER  ),
        IGNORE_ERROR_KEEP_BAD_EXPRESSION( IGNORE_HANDLER_KEEP_BAD_EXPRESSION ),
        RETHROW_EXCEPTION_ON_ERROR( TemplateExceptionHandler.RETHROW_HANDLER );

        final TemplateExceptionHandler templateExceptionHandler;

        ErrorHandlerEnum(TemplateExceptionHandler templateExceptionHandler) {
            this.templateExceptionHandler = templateExceptionHandler;
        }

        public TemplateExceptionHandler value() {
            return templateExceptionHandler;
        }
    }

    /**
     * All expression(e.g. ${test}) must be processed in template file else exception will happen to stop processing template.
     * Use processTemplate(String templateName, Map<String, Object> dataMap, ErrorHandlerEnum errorHandlerEnum)
     * with errorHandlerEnum = IGNORE_ERROR_KEEP_BAD_EXPRESSION to keep processing and keeps bad expression causing the
     * error in the processed template.
     *
     * @param templateName the name of the template file to process in /templates folder
     * @param dataMap      the map where each entry key represents the expression name in template file and the
     *                     associated key will be used to replace the expression in template file
     * @return             the string representing the processed template file
     */
    public String processTemplate(String templateName, Map<String, Object> dataMap)
            throws TemplateException, IOException {

        return processTemplate(templateName, dataMap, ErrorHandlerEnum.RETHROW_EXCEPTION_ON_ERROR);
    }

    public String processTemplate(String templateName, Map<String, Object> dataMap, ErrorHandlerEnum errorHandlerEnum)
            throws TemplateException, IOException {

        StringWriter stringWriter = new StringWriter();
        try  {

            Template template = freeMarkerConfigurer.getConfiguration().getTemplate( templateName );
            template.setTemplateExceptionHandler( errorHandlerEnum.value() );
            template.process(dataMap, stringWriter);

        } catch (Exception ex) {
            log.error("Failed to process Freemarker template '" + templateName +  "'!", ex);
            throw ex;
        }
        return stringWriter.toString();
    }

    /**
     * Keep bad expression as '${expression}' in template when it causes error and keep processing. Useful for testing.
     */
    static final TemplateExceptionHandler IGNORE_HANDLER_KEEP_BAD_EXPRESSION = new TemplateExceptionHandler() {
        public void handleTemplateException(TemplateException te, Environment env, Writer out) throws TemplateException {
            try {
                out.write("${" + te.getBlamedExpressionString() + "}");
            } catch (IOException e) {
                throw new TemplateException("Failed to print error message. Cause: " + e, env);
            }
        }
    };

}
