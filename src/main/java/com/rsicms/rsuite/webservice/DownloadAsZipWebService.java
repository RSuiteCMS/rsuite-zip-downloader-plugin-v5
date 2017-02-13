package com.rsicms.rsuite.webservice;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.reallysi.rsuite.api.ContentAssemblyNodeContainer;
import com.reallysi.rsuite.api.ManagedObject;
import com.reallysi.rsuite.api.RSuiteException;
import com.reallysi.rsuite.api.User;
import com.reallysi.rsuite.api.remoteapi.CallArgumentList;
import com.reallysi.rsuite.api.remoteapi.DefaultRemoteApiHandler;
import com.reallysi.rsuite.api.remoteapi.RemoteApiExecutionContext;
import com.reallysi.rsuite.api.remoteapi.RemoteApiResult;
import com.reallysi.rsuite.api.system.RSuiteServerConfiguration;
import com.rsicms.rsuite.helpers.download.ZipHelper;
import com.rsicms.rsuite.helpers.utils.RSuiteUtils;

/**
 * Packages the specified managed objects and containers as a single Zip file
 * for download.
 * 
 */
public class DownloadAsZipWebService extends DefaultRemoteApiHandler {

	private static final String ERROR_TEMPLATE = 
			"<html><head><title>%1$s</title></head><body>%1$s: %2$s</body></head>";
	private static final String ERROR_IO_TITLE = 
			"I/O Exception creating Zip output stream";
	private static final String ZIP_CONTENT_TYPE = "application/zip";
	private static final SimpleDateFormat SDF = 
			new SimpleDateFormat("yyyyMMddHHmmssSSS");
	private static Log log = LogFactory.getLog(DownloadAsZipWebService.class);
	
    @Override
    public RemoteApiResult execute(
            RemoteApiExecutionContext context, CallArgumentList args) 
    throws RSuiteException {

        User user = context.getSession().getUser();
        List<ManagedObject> moList = args.getManagedObjects(user);
        FileResult result = null;

        String suggestedFilename = "rsuite-archive.zip";
        if (moList.size() == 0) {
            // Need to return a MessageDialogResult or notification result
            // in this case.
        } else {
            if (moList.size() == 1) {
                String filename = moList.get(0).getDisplayName();
                String basename = FilenameUtils.getBaseName(filename);
                suggestedFilename = basename + ".zip" ;
            }

            RSuiteServerConfiguration config = 
            		context.getRSuiteServerConfiguration();
            File tempDir = config.getTmpDir();
            File zipDir = new File(tempDir, "zip" + SDF.format(new Date()));
            if (!zipDir.exists()) {
            	zipDir.mkdirs();
            }
            File file = new File(zipDir, suggestedFilename);
            FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(file);
			} catch (FileNotFoundException e) {
				String msg = "Error creating zip file output stream.";
				log.error(msg, e);
				throw new RSuiteException(0, msg, e);
			}
			
            ZipHelper zipHelper = new ZipHelper(context, fos);
            StringBuilder messageStr = new StringBuilder();
            
            for (ManagedObject mo : moList) {
            	try {
	                String targetId = mo.getTargetId();
	                if (targetId != null) {
	                    mo = context.getManagedObjectService().getManagedObject(user, targetId);
	                }
	                if (mo.isAssemblyNode()) {
	                    // Add the container to the Zip
	                    ContentAssemblyNodeContainer container = RSuiteUtils
	                            .getContentAssemblyNodeContainer(context, user, mo.getId());
	                    zipHelper.addCaNodeContentsToZip(container.getDisplayName() + "/", container);
	                } else {
	                	zipHelper.addManagedObjectToZip("", mo);
	                }
	            } catch (IOException e) {
	                messageStr.append(e.getMessage() + "<br>");
	            }
           	}
            if (messageStr.length() != 0) {
            	String errorMsg = String.format(ERROR_TEMPLATE, ERROR_IO_TITLE, "<p>" + messageStr.toString() + "</p>");
                /*
                 * TODO: This will be logged as an enhancement for a future release and it will be decided how the message
                 * will be presented to the user and also if some options will be provided as well for the ZIP file.
            	try {
                    result.setContent(errorMsg.getBytes("utf-8"));
                    result.setContentType("text/html");
                } catch (UnsupportedEncodingException e) {
                    // Won't happen.
                }
                */
            	log.error(ERROR_IO_TITLE + ": " + errorMsg);
            }
            zipHelper.closeZipOutputStream();

            result = new FileResult(file);
            result.setContentType(ZIP_CONTENT_TYPE);
            result.setSuggestedFileName(suggestedFilename);
        }

        return result;
    }
}