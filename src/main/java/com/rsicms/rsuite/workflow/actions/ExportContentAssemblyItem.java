package com.rsicms.rsuite.workflow.actions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;

import com.reallysi.rsuite.api.ContentAssemblyNodeContainer;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.control.ContentAssemblyItemFilter;
import com.reallysi.rsuite.api.workflow.AbstractBaseActionHandler;
import com.reallysi.rsuite.api.workflow.WorkflowExecutionContext;
import com.rsicms.rsuite.helpers.download.ZipHelper;
import com.rsicms.rsuite.helpers.download.ZipHelperConfiguration;
import com.rsicms.rsuite.helpers.utils.FileTypeCaItemFilter;
import com.rsicms.rsuite.helpers.utils.RSuiteUtils;

public class ExportContentAssemblyItem extends AbstractBaseActionHandler {
	
	private static final long serialVersionUID = -5582513866944142398L;

	/**
	 * Comma-delimited list of prefixes or content types to include
	 */
	public static final String PARAM_INCLUDE_EXTENSIONS = "includeExtensions";
	/**
	 * Comma-delimited list of prefixes or content types to exclude
	 */
	public static final String PARAM_EXCLUDE_EXTENSIONS = "excludeExtensions";

	public static final String DEFAULT_EXPORT_PATH = "/opt/rsuite-data/export/rsi/temp";
	/**
	 * Path on server file system where the result Zip file should be created.
	 */
	private static final String PARAM_EXPORT_PATH = "exportPath";

	/**
	 * A distinguishing suffix to add to the filename before the product ID.
	 */
	private static final String PARAM_FILENAME_SUFFIX = "filenameSuffix";

	/**
	 * Value, such as publication code, DOI, ISBN, or other unique value
	 * that will be used to correlate the exported Zip file to the content
	 * assembly item it reflects. If not specified, uses the managed object
	 * ID of the content assembly node.
	 */
	public static final String PARAM_ZIP_CA_CORRELATION_KEY = "zipCaCorrelationKey";

	@Override
	public void execute(WorkflowExecutionContext context) throws Exception {
		Log wfLog = context.getWorkflowLog();
		
		wfLog.info("Export content assembly: Starting...");
		String exportPath = 
				resolveVariablesAndExpressions(
					context, 
					getParameterWithDefault(PARAM_EXPORT_PATH, DEFAULT_EXPORT_PATH));
		wfLog.info("exportPath=\"" + exportPath + "\"");
		
		String filenameSuffix = 
				resolveVariablesAndExpressions(
					context, 
					getParameterWithDefault(PARAM_FILENAME_SUFFIX, null));
		wfLog.info("filenameSuffix=\"" + filenameSuffix + "\"");

		List<String> includeExtensions = new ArrayList<String>();
		List<String> excludeExtensions = new ArrayList<String>();

		String includeExtensionsParam = 
				resolveVariablesAndExpressions(
					context, 
					getParameter(PARAM_INCLUDE_EXTENSIONS));	
		wfLog.info("includeExtensions=\"" + includeExtensionsParam + "\"");
		
		String excludeExtensionsParam =  
				resolveVariablesAndExpressions(
						context, 
						getParameter(PARAM_EXCLUDE_EXTENSIONS));
		wfLog.info("excludeExtensionsParam=\"" + excludeExtensionsParam + "\"");

		String zipCaCorrelationKey =  
				resolveVariablesAndExpressions(
						context, 
						getParameter(PARAM_ZIP_CA_CORRELATION_KEY));
		wfLog.info("zipCaCorrelationKey=\"" + zipCaCorrelationKey + "\"");

		if (includeExtensionsParam != null) { 
			if (includeExtensionsParam.contains(",")) {
				for (String ext : includeExtensionsParam.split(",")) {
					includeExtensions.add(ext.toLowerCase());
				}
			} else {
				includeExtensions.add(includeExtensionsParam.toLowerCase());
			}
		}
		if (excludeExtensionsParam != null) { 
			if (excludeExtensionsParam.contains(",")) {
				for (String ext : excludeExtensionsParam.split(",")) {
					excludeExtensions.add(ext.toLowerCase());
				}
			} else {
				excludeExtensions.add(excludeExtensionsParam.toLowerCase());
			}
		}
		
		
		ContentAssemblyItemFilter filter = 
				new FileTypeCaItemFilter(
						context,
						includeExtensions, 
						excludeExtensions);

		wfLog.info("Doing export of content assembly...");
		doExport(
				context,
				exportPath, 
				filenameSuffix,
				zipCaCorrelationKey,
				filter);
		wfLog.info("Export complete.");
	}
	
	/**
	 * 
	 * @param context Execution context
	 * @param exportPath The path to export the result Zip file to.
	 * @param filenameSuffix An additional field to add to the Zip filename. 
	 * @param zipCaCorrelationKey Key used to correlate the Zip file to CA it was generated from.
	 * @param filter The CA item filter used to select MOs to export.
	 * @throws RSuiteException
	 */
	public static void doExport(
			WorkflowExecutionContext context,
			String exportPath, 
			String filenameSuffix, 
			String zipCaCorrelationKey, 
			ContentAssemblyItemFilter filter) throws RSuiteException {

		User user = context.getAuthorizationService().getSystemUser();

		File exportFolder = new File(exportPath);
		if (!exportFolder.exists()) {
			if (!exportFolder.mkdirs()) {
				logError(context, "Failed to create export directory \"" + exportFolder.getAbsolutePath() + "\"");
				return;
			}
		}

		String caId = context.getVariable("rsuite contents");
		if (caId == null || "".equals(caId.trim())) {
			logError(context, "No 'rsuite contents' variable in the workflow context");
			return;
		}

		ContentAssemblyNodeContainer container;
		try {
			container = RSuiteUtils.getContentAssemblyNodeContainer(context, user, caId);
		} catch (Exception e) {
			logError(context,
					"Failed to get container with ID [" + caId + "]");
			return;
		}
		
		if (zipCaCorrelationKey == null || "".equals(zipCaCorrelationKey.trim())) {
			context.getWorkflowLog().info("No value for " + PARAM_ZIP_CA_CORRELATION_KEY + ", using assembly node ID [" + caId + "]");
			zipCaCorrelationKey = caId;
		}
		
		String containerName = container.getDisplayName();
		containerName = containerName.replaceAll("\\s+", "_");

		String exportFileName = containerName + "_";
		if (filenameSuffix != null) {
			exportFileName += filenameSuffix + "_";
		}
		exportFileName += zipCaCorrelationKey;

		File exportFile = new File(exportFolder, exportFileName + ".zip");
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(exportFile);
		} catch (FileNotFoundException e) {
			logError(context, 
					"execute(): FileNotFound exception creating file output stream: " + e.getMessage());
			return;
		}
		
		
		
		ZipHelperConfiguration configuration = new ZipHelperConfiguration();
		configuration.setCaItemFilter(filter);
		
		context.getWorkflowLog().info("Constructing Zip for content assembly item " +
				"[" + container.getId() + "] " + container.getDisplayName() + "..."); 

		try {
			ZipHelper.zipContentAssemblyContents(context, container, fos, configuration);
			context.getWorkflowLog().info("Zip created as \"" + exportFile.getAbsolutePath() + "\"");
		} catch (Exception e) {
			logError(context, 
					e.getClass().getSimpleName() + " exception constructing Zip for export: " + e.getMessage(), 
					e);
		}
	}
	
	public static void logError(
			WorkflowExecutionContext context,
			String message, 
			Exception e) throws RSuiteException {
		context.getWorkflowLog().error(message, e);
		context.setVariable("failureDetail", message);
		context.setVariable("EXCEPTION_OCCUR", "true");
		context.setVariable("EXCEPTION_TYPE", "EXCEPTION_TYPE_BUSINESSRULE");
		
	}

	public static void logError(
			WorkflowExecutionContext context, 
			String message) throws RSuiteException {
		context.getWorkflowLog().error(message);
		context.setVariable("failureDetail", message);
		context.setVariable("EXCEPTION_OCCUR", "true");
		context.setVariable("EXCEPTION_TYPE", "EXCEPTION_TYPE_BUSINESSRULE");
	}

	public void setIncludeExtensions(String includeExtensions) {
		setParameter(PARAM_INCLUDE_EXTENSIONS, includeExtensions);
	}

	public void setExcludeExtensions(String excludeExtensions) {
		setParameter(PARAM_EXCLUDE_EXTENSIONS, excludeExtensions);
	}

	public void setExportPath(String exportPath) {
		setParameter(PARAM_EXPORT_PATH, exportPath);
	}

	public void setFilenameSuffix(String filenameSuffix) {
		setParameter(PARAM_FILENAME_SUFFIX, filenameSuffix);
	}

	public void setZipCaCorrelationKey(String zipCaCorrelationKey) {
		setParameter(PARAM_ZIP_CA_CORRELATION_KEY, zipCaCorrelationKey);
	}

}
