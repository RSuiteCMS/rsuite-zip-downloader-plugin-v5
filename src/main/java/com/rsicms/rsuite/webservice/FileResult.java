package com.rsicms.rsuite.webservice;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.reallysi.rsuite.api.remoteapi.DefaultRemoteApiResult;

public class FileResult extends DefaultRemoteApiResult {

	private File file;
	
	public FileResult(File file) {
		this.file = file;
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		return new FileInputStream(file);
	}
}
