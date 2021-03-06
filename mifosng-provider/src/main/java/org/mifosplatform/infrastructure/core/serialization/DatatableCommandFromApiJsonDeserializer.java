/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.infrastructure.core.serialization;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.mifosplatform.infrastructure.core.data.ApiParameterError;
import org.mifosplatform.infrastructure.core.data.DataValidatorBuilder;
import org.mifosplatform.infrastructure.core.exception.InvalidJsonException;
import org.mifosplatform.infrastructure.core.exception.PlatformApiDataValidationException;
import org.mifosplatform.infrastructure.core.exception.PlatformDataIntegrityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

@Component
public class DatatableCommandFromApiJsonDeserializer {

	private final static String DATATABLE_NAME_REGEX_PATTERN = "^[a-zA-Z][a-zA-Z0-9\\-_\\s]{0,48}[a-zA-Z0-9]$";
	private final static String DATATABLE_COLUMN_NAME_REGEX_PATTERN = "^[a-zA-Z][a-zA-Z0-9\\-_\\s]{0,}[a-zA-Z0-9]$";
	/**
     * The parameters supported for this command.
     */
    private final Set<String> supportedParametersForCreate = new HashSet<String>(Arrays.asList(
    		"datatableName", "apptableName", "multiRow", "columns"));
    private final Set<String> supportedParametersForCreateColumns = new HashSet<String>(Arrays.asList(
    		"name", "type", "length", "mandatory", "code"));
    private final Set<String> supportedParametersForUpdate = new HashSet<String>(Arrays.asList(
    		"apptableName", "changeColumns", "addColumns", "dropColumns"));
    private final Set<String> supportedParametersForAddColumns = new HashSet<String>(Arrays.asList(
    		"name", "type", "length", "mandatory", "after", "code"));
    private final Set<String> supportedParametersForChangeColumns = new HashSet<String>(Arrays.asList(
    		"name", "newName", "length", "mandatory", "after", "code", "newCode"));
    private final Set<String> supportedParametersForDropColumns = new HashSet<String>(Arrays.asList(
    		"name"));
    private final Object[] supportedColumnTypes = { "String", "Number", "Decimal", "Date", "Text", "Dropdown" };
    private final Object[] supportedApptableNames = { "m_loan", "m_savings_account", "m_client", "m_group", "m_center", "m_office", "m_savings_product", "m_product_loan" };

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public DatatableCommandFromApiJsonDeserializer(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    private void validateType(final DataValidatorBuilder baseDataValidator, final JsonElement column) {
    	final String type = fromApiJsonHelper.extractStringNamed("type", column);
    	baseDataValidator.reset().parameter("type").value(type).notBlank()
    		.isOneOfTheseValues(supportedColumnTypes);

    	if (type != null && type.equals("String")) {
    		if (fromApiJsonHelper.parameterExists("length", column)) {
    			final String lengthStr = fromApiJsonHelper.extractStringNamed("length", column);
	    		if (lengthStr != null && !StringUtils.isWhitespace(lengthStr)
	    				&& StringUtils.isNumeric(lengthStr) && StringUtils.isNotBlank(lengthStr)) {
	    			Integer length = Integer.parseInt(lengthStr);
	    			baseDataValidator.reset().parameter("length").value(length).positiveAmount();
	    		} else if (StringUtils.isBlank(lengthStr) || StringUtils.isWhitespace(lengthStr)) {
	    			baseDataValidator.reset().parameter("length").failWithCode("must.be.provided.when.type.is.String");
	    		} else if (!StringUtils.isNumeric(lengthStr)) {
	    			baseDataValidator.reset().parameter("length").failWithCode("not.greater.than.zero");
	    		}
    		} else {
    			baseDataValidator.reset().parameter("length").failWithCode("must.be.provided.when.type.is.String");
    		}
    	} else {
    		baseDataValidator.reset().parameter("length").mustBeBlankWhenParameterProvidedIs("type", type);
    	}
    	
    	final String code = fromApiJsonHelper.extractStringNamed("code", column);
    	if (type != null && type.equals("Dropdown")) {
    		if (code != null) {
    			baseDataValidator.reset().parameter("code").value(code).notBlank()
        			.matchesRegularExpression(DATATABLE_NAME_REGEX_PATTERN);
    		} else {
    			baseDataValidator.reset().parameter("code").value(code)
    				.cantBeBlankWhenParameterProvidedIs("type", type);
    		}
    	} else {
    		baseDataValidator.reset().parameter("code").value(code).mustBeBlankWhenParameterProvided("type", type);
    	}
    }

    public void validateForCreate(final String json) {
        if (StringUtils.isBlank(json)) { throw new InvalidJsonException(); }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParametersForCreate);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("datatable");

        final JsonElement element = fromApiJsonHelper.parse(json);
        
        final String datatableName = fromApiJsonHelper.extractStringNamed("datatableName", element);
        baseDataValidator.reset().parameter("datatableName").value(datatableName).notBlank().notExceedingLengthOf(50)
        	.matchesRegularExpression(DATATABLE_NAME_REGEX_PATTERN);
        
        final String apptableName = fromApiJsonHelper.extractStringNamed("apptableName", element);
        baseDataValidator.reset().parameter("apptableName").value(apptableName).notBlank().notExceedingLengthOf(50)
        	.isOneOfTheseValues(supportedApptableNames);
        final String fkColumnName = (apptableName != null) ? apptableName.substring(2) + "_id" : "";

        final Boolean multiRow = fromApiJsonHelper.extractBooleanNamed("multiRow", element);
        baseDataValidator.reset().parameter("multiRow").value(multiRow).ignoreIfNull().notBlank()
			.isOneOfTheseValues(true, false);

        final JsonArray columns = fromApiJsonHelper.extractJsonArrayNamed("columns", element);
        baseDataValidator.reset().parameter("columns").value(columns).notNull().jsonArrayNotEmpty();

        if (columns != null) {
	        for (JsonElement column : columns) {
	        	fromApiJsonHelper.checkForUnsupportedParameters(column.getAsJsonObject(), supportedParametersForCreateColumns);

	        	final String name = fromApiJsonHelper.extractStringNamed("name", column);
	        	baseDataValidator.reset().parameter("name").value(name).notBlank()
	        		.isNotOneOfTheseValues("id", fkColumnName).matchesRegularExpression(DATATABLE_COLUMN_NAME_REGEX_PATTERN);

	        	validateType(baseDataValidator, column);

	        	final Boolean mandatory = fromApiJsonHelper.extractBooleanNamed("mandatory", column);
	        	baseDataValidator.reset().parameter("mandatory").value(mandatory).ignoreIfNull().notBlank()
	        		.isOneOfTheseValues(true, false);
	        }
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateForUpdate(final String json) {
        if (StringUtils.isBlank(json)) { throw new InvalidJsonException(); }
        // Because all parameters are optional, a check to see if at least one parameter
        // has been specified is necessary in order to avoid JSON requests with no parameters
        if (!json.matches("(?s)\\A\\{.*?(\\\".*?\\\"\\s*?:\\s*?)+.*?\\}\\z")) {
        	throw new PlatformDataIntegrityException("error.msg.invalid.request.body.no.parameters",
        			"Provided JSON request body does not have any parameters.");
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParametersForUpdate);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<ApiParameterError>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("datatable");

        final JsonElement element = fromApiJsonHelper.parse(json);
        final String apptableName = fromApiJsonHelper.extractStringNamed("apptableName", element);
        baseDataValidator.reset().parameter("apptableName").value(apptableName).ignoreIfNull().notBlank()
        	.isOneOfTheseValues(supportedApptableNames);
        final String fkColumnName = (apptableName != null) ? apptableName.substring(2) + "_id" : "";

        final JsonArray changeColumns = fromApiJsonHelper.extractJsonArrayNamed("changeColumns", element);
        baseDataValidator.reset().parameter("changeColumns").value(changeColumns).ignoreIfNull().jsonArrayNotEmpty();

        if (changeColumns != null) {
	        for (JsonElement column : changeColumns) {
	        	fromApiJsonHelper.checkForUnsupportedParameters(column.getAsJsonObject(), supportedParametersForChangeColumns);

	        	final String name = fromApiJsonHelper.extractStringNamed("name", column);
	        	baseDataValidator.reset().parameter("name").value(name).notBlank()
	        		.isNotOneOfTheseValues("id", fkColumnName).matchesRegularExpression(DATATABLE_COLUMN_NAME_REGEX_PATTERN);

	        	final String newName = fromApiJsonHelper.extractStringNamed("newName", column);
	        	baseDataValidator.reset().parameter("newName").value(newName).ignoreIfNull().notBlank()
	        		.notExceedingLengthOf(50).isNotOneOfTheseValues("id", fkColumnName)
	        		.matchesRegularExpression(DATATABLE_NAME_REGEX_PATTERN);

	        	if (fromApiJsonHelper.parameterExists("length", column)) {
		        	final String lengthStr = fromApiJsonHelper.extractStringNamed("length", column);
	        		if (StringUtils.isWhitespace(lengthStr) || !StringUtils.isNumeric(lengthStr)
	        				|| StringUtils.isBlank(lengthStr)) {
	        			baseDataValidator.reset().parameter("length").failWithCode("not.greater.than.zero");
	        		} else {
	        			Integer length = Integer.parseInt(lengthStr);
	        			baseDataValidator.reset().parameter("length").value(length).ignoreIfNull().notBlank()
    						.positiveAmount();
		        	}
	        	}

	        	final String code = fromApiJsonHelper.extractStringNamed("code", column);
	        	baseDataValidator.reset().parameter("code").value(code).ignoreIfNull().notBlank()
	        		.notExceedingLengthOf(100).matchesRegularExpression(DATATABLE_COLUMN_NAME_REGEX_PATTERN);

	        	final String newCode = fromApiJsonHelper.extractStringNamed("newCode", column);
	        	baseDataValidator.reset().parameter("newCode").value(newCode).ignoreIfNull().notBlank()
	        		.notExceedingLengthOf(100).matchesRegularExpression(DATATABLE_COLUMN_NAME_REGEX_PATTERN);

	        	if (StringUtils.isBlank(code) && StringUtils.isNotBlank(newCode)) {
	        		baseDataValidator.reset().parameter("code").value(code)
	        			.cantBeBlankWhenParameterProvidedIs("newCode", newCode);
	        	}

	        	final Boolean mandatory = fromApiJsonHelper.extractBooleanNamed("mandatory", column);
	        	baseDataValidator.reset().parameter("mandatory").value(mandatory).ignoreIfNull().notBlank()
					.isOneOfTheseValues(true, false);

	        	final Boolean after = fromApiJsonHelper.extractBooleanNamed("after", column);
	        	baseDataValidator.reset().parameter("after").value(after).ignoreIfNull().notBlank()
	    			.isOneOfTheseValues(true, false);
	        }
        }

        final JsonArray addColumns = fromApiJsonHelper.extractJsonArrayNamed("addColumns", element);
        baseDataValidator.reset().parameter("addColumns").value(addColumns).ignoreIfNull().jsonArrayNotEmpty();

        if (addColumns != null) {
	        for (JsonElement column : addColumns) {
	        	fromApiJsonHelper.checkForUnsupportedParameters(column.getAsJsonObject(), supportedParametersForAddColumns);

	        	final String name = fromApiJsonHelper.extractStringNamed("name", column);
	        	baseDataValidator.reset().parameter("name").value(name).notBlank()
	        		.isNotOneOfTheseValues("id", fkColumnName).matchesRegularExpression(DATATABLE_COLUMN_NAME_REGEX_PATTERN);

	        	validateType(baseDataValidator, column);

	        	final Boolean mandatory = fromApiJsonHelper.extractBooleanNamed("mandatory", column);
	        	baseDataValidator.reset().parameter("mandatory").value(mandatory).ignoreIfNull().notBlank()
    				.isOneOfTheseValues(true, false);

	        	final Boolean after = fromApiJsonHelper.extractBooleanNamed("after", column);
	        	baseDataValidator.reset().parameter("after").value(after).ignoreIfNull().notBlank()
    				.isOneOfTheseValues(true, false);
	        }
        }

    	final JsonArray dropColumns = fromApiJsonHelper.extractJsonArrayNamed("dropColumns", element);
        baseDataValidator.reset().parameter("dropColumns").value(dropColumns).ignoreIfNull().jsonArrayNotEmpty();

	    if (dropColumns != null) {
	        for (JsonElement column : dropColumns) {
	        	fromApiJsonHelper.checkForUnsupportedParameters(column.getAsJsonObject(), supportedParametersForDropColumns);

	        	final String name = fromApiJsonHelper.extractStringNamed("name", column);
	        	baseDataValidator.reset().parameter("name").value(name).notBlank()
	        		.isNotOneOfTheseValues("id", fkColumnName).matchesRegularExpression(DATATABLE_COLUMN_NAME_REGEX_PATTERN);
	        }
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) { throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist",
                "Validation errors exist.", dataValidationErrors); }
    }
}
