/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.workflow.actions.http;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.Result;
import org.apache.hop.core.ResultFile;
import org.apache.hop.core.RowMetaAndData;
import org.apache.hop.core.annotations.Action;
import org.apache.hop.core.encryption.Encr;
import org.apache.hop.core.exception.HopXmlException;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.util.HttpClientManager;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.resource.ResourceEntry;
import org.apache.hop.resource.ResourceEntry.ResourceType;
import org.apache.hop.resource.ResourceReference;
import org.apache.hop.workflow.WorkflowMeta;
import org.apache.hop.workflow.action.ActionBase;
import org.apache.hop.workflow.action.IAction;
import org.apache.hop.workflow.action.validator.ActionValidatorUtils;
import org.apache.hop.workflow.action.validator.AndValidator;
import org.w3c.dom.Node;

/** This defines an HTTP action. */
@Action(
    id = "HTTP",
    name = "i18n::ActionHTTP.Name",
    description = "i18n::ActionHTTP.Description",
    image = "HTTP.svg",
    categoryDescription = "i18n:org.apache.hop.workflow:ActionCategory.Category.FileManagement",
    keywords = "i18n::ActionHttp.keyword",
    documentationUrl = "/workflow/actions/http.html")
@Getter
@Setter
public class ActionHttp extends ActionBase implements Cloneable, IAction {
  public static final String SPACES = "      ";
  private static final Class<?> PKG = ActionHttp.class;

  private static final String CONST_URL_FIELDNAME = "URL";
  private static final String COSNT_UPLOADFILE_FIELDNAME = "UPLOAD";
  private static final String CONST_TARGETFILE_FIELDNAME = "DESTINATION";
  public static final String CONST_HTTP_PROXY_HOST = "http.proxyHost";
  public static final String CONST_HTTP_PROXY_PORT = "http.proxyPort";
  public static final String CONST_HTTPS_PROXY_HOST = "https.proxyHost";
  public static final String CONST_HTTPS_PROXY_PORT = "https.proxyPort";
  public static final String CONST_HTTP_NON_PROXY_HOSTS = "http.nonProxyHosts";

  // Base info
  private String url;

  private String targetFilename;

  private boolean fileAppended;

  private boolean dateTimeAdded;

  private String targetFilenameExtension;

  // Send file content to server?
  private String uploadFilename;

  // The fieldname that contains the URL
  // Get it from a previous pipeline with Result.
  private String urlFieldname;
  private String uploadFieldname;
  private String destinationFieldname;

  private boolean runForEveryRow;

  private boolean ignoreSsl;

  // Proxy settings
  private String proxyHostname;

  private String proxyPort;

  private String nonProxyHosts;

  private String username;

  private String password;

  private boolean addfilenameresult;

  private String[] headerName;

  private String[] headerValue;

  public ActionHttp(String n) {
    super(n, "");
    url = null;
    addfilenameresult = true;
  }

  public ActionHttp() {
    this("");
  }

  private void allocate(int nrHeaders) {
    headerName = new String[nrHeaders];
    headerValue = new String[nrHeaders];
  }

  @Override
  public Object clone() {
    ActionHttp je = (ActionHttp) super.clone();
    if (headerName != null) {
      int nrHeaders = headerName.length;
      je.allocate(nrHeaders);
      System.arraycopy(headerName, 0, je.headerName, 0, nrHeaders);
      System.arraycopy(headerValue, 0, je.headerValue, 0, nrHeaders);
    }
    return je;
  }

  @Override
  public String getXml() {
    StringBuilder retval = new StringBuilder(300);

    retval.append(super.getXml());
    retval.append(SPACES).append(XmlHandler.addTagValue("url", url));
    retval.append(SPACES).append(XmlHandler.addTagValue("targetfilename", targetFilename));
    retval.append(SPACES).append(XmlHandler.addTagValue("file_appended", fileAppended));
    retval.append(SPACES).append(XmlHandler.addTagValue("date_time_added", dateTimeAdded));
    retval
        .append(SPACES)
        .append(XmlHandler.addTagValue("targetfilename_extension", targetFilenameExtension));
    retval.append(SPACES).append(XmlHandler.addTagValue("uploadfilename", uploadFilename));

    retval.append(SPACES).append(XmlHandler.addTagValue("run_every_row", runForEveryRow));
    retval.append(SPACES).append(XmlHandler.addTagValue("ignore_ssl", ignoreSsl));
    retval.append(SPACES).append(XmlHandler.addTagValue("url_fieldname", urlFieldname));
    retval.append(SPACES).append(XmlHandler.addTagValue("upload_fieldname", uploadFieldname));
    retval.append(SPACES).append(XmlHandler.addTagValue("dest_fieldname", destinationFieldname));

    retval.append(SPACES).append(XmlHandler.addTagValue("username", username));
    retval
        .append(SPACES)
        .append(
            XmlHandler.addTagValue("password", Encr.encryptPasswordIfNotUsingVariables(password)));

    retval.append(SPACES).append(XmlHandler.addTagValue("proxy_host", proxyHostname));
    retval.append(SPACES).append(XmlHandler.addTagValue("proxy_port", proxyPort));
    retval.append(SPACES).append(XmlHandler.addTagValue("non_proxy_hosts", nonProxyHosts));
    retval.append(SPACES).append(XmlHandler.addTagValue("addfilenameresult", addfilenameresult));
    retval.append("      <headers>").append(Const.CR);
    if (headerName != null) {
      for (int i = 0; i < headerName.length; i++) {
        retval.append("        <header>").append(Const.CR);
        retval.append("          ").append(XmlHandler.addTagValue("header_name", headerName[i]));
        retval.append("          ").append(XmlHandler.addTagValue("header_value", headerValue[i]));
        retval.append("        </header>").append(Const.CR);
      }
    }
    retval.append("      </headers>").append(Const.CR);

    return retval.toString();
  }

  @Override
  public void loadXml(Node entrynode, IHopMetadataProvider metadataProvider, IVariables variables)
      throws HopXmlException {
    try {
      super.loadXml(entrynode);
      url = XmlHandler.getTagValue(entrynode, "url");
      targetFilename = XmlHandler.getTagValue(entrynode, "targetfilename");
      fileAppended = "Y".equalsIgnoreCase(XmlHandler.getTagValue(entrynode, "file_appended"));
      dateTimeAdded = "Y".equalsIgnoreCase(XmlHandler.getTagValue(entrynode, "date_time_added"));
      targetFilenameExtension =
          Const.NVL(
              XmlHandler.getTagValue(entrynode, "targetfilename_extension"),
              XmlHandler.getTagValue(entrynode, "targetfilename_extention"));

      uploadFilename = XmlHandler.getTagValue(entrynode, "uploadfilename");

      urlFieldname = XmlHandler.getTagValue(entrynode, "url_fieldname");
      uploadFieldname = XmlHandler.getTagValue(entrynode, "upload_fieldname");
      destinationFieldname = XmlHandler.getTagValue(entrynode, "dest_fieldname");
      runForEveryRow = "Y".equalsIgnoreCase(XmlHandler.getTagValue(entrynode, "run_every_row"));
      ignoreSsl = "Y".equalsIgnoreCase(XmlHandler.getTagValue(entrynode, "ignore_ssl"));

      username = XmlHandler.getTagValue(entrynode, "username");
      password =
          Encr.decryptPasswordOptionallyEncrypted(XmlHandler.getTagValue(entrynode, "password"));

      proxyHostname = XmlHandler.getTagValue(entrynode, "proxy_host");
      proxyPort = XmlHandler.getTagValue(entrynode, "proxy_port");
      nonProxyHosts = XmlHandler.getTagValue(entrynode, "non_proxy_hosts");
      addfilenameresult =
          "Y"
              .equalsIgnoreCase(
                  Const.NVL(XmlHandler.getTagValue(entrynode, "addfilenameresult"), "Y"));
      Node headers = XmlHandler.getSubNode(entrynode, "headers");

      // How many field headerName?
      int nrHeaders = XmlHandler.countNodes(headers, "header");
      allocate(nrHeaders);
      for (int i = 0; i < nrHeaders; i++) {
        Node fnode = XmlHandler.getSubNodeByNr(headers, "header", i);
        headerName[i] = XmlHandler.getTagValue(fnode, "header_name");
        headerValue[i] = XmlHandler.getTagValue(fnode, "header_value");
      }
    } catch (HopXmlException xe) {
      throw new HopXmlException("Unable to load action of type 'HTTP' from XML node", xe);
    }
  }

  public boolean isAddFilenameToResult() {
    return addfilenameresult;
  }

  public void setAddFilenameToResult(boolean addfilenameresult) {
    this.addfilenameresult = addfilenameresult;
  }

  /**
   * We made this one synchronized in the JVM because otherwise, this is not thread safe. In that
   * case if (on an application server for example) several HTTP's are running at the same time, you
   * get into problems because the System.setProperty() calls are system wide!
   */
  @Override
  public synchronized Result execute(Result previousResult, int nr) {
    Result result = previousResult;
    result.setResult(false);

    logBasic(BaseMessages.getString(PKG, "ActionHTTP.StartAction"));

    // Get previous result rows...
    List<RowMetaAndData> resultRows;
    String urlFieldnameToUse;
    String uploadFieldnameToUse;
    String destinationFieldnameToUse;

    if (Utils.isEmpty(urlFieldname)) {
      urlFieldnameToUse = CONST_URL_FIELDNAME;
    } else {
      urlFieldnameToUse = urlFieldname;
    }

    if (Utils.isEmpty(uploadFieldname)) {
      uploadFieldnameToUse = COSNT_UPLOADFILE_FIELDNAME;
    } else {
      uploadFieldnameToUse = uploadFieldname;
    }

    if (Utils.isEmpty(destinationFieldname)) {
      destinationFieldnameToUse = CONST_TARGETFILE_FIELDNAME;
    } else {
      destinationFieldnameToUse = destinationFieldname;
    }

    if (runForEveryRow) {
      resultRows = previousResult.getRows();
      if (resultRows == null) {
        result.setNrErrors(1);
        logError(BaseMessages.getString(PKG, "ActionHTTP.Error.UnableGetResultPrevious"));
        return result;
      }
    } else {
      resultRows = new ArrayList<>();
      RowMetaAndData row = new RowMetaAndData();
      row.addValue(new ValueMetaString(urlFieldnameToUse), resolve(url));
      row.addValue(new ValueMetaString(uploadFieldnameToUse), resolve(uploadFilename));
      row.addValue(new ValueMetaString(destinationFieldnameToUse), resolve(targetFilename));
      resultRows.add(row);
    }

    URL server = null;

    String beforeProxyHost = getVariable(CONST_HTTP_PROXY_HOST);
    String beforeProxyPort = getVariable(CONST_HTTP_PROXY_PORT);
    String beforeHttpsProxyHost = getVariable(CONST_HTTPS_PROXY_HOST);
    String beforeHttpsProxyPort = getVariable(CONST_HTTPS_PROXY_PORT);
    String beforeNonProxyHosts = getVariable(CONST_HTTP_NON_PROXY_HOSTS);

    for (int i = 0; i < resultRows.size() && result.getNrErrors() == 0; i++) {
      RowMetaAndData row = resultRows.get(i);

      OutputStream outputFile = null;
      OutputStream uploadStream = null;
      BufferedInputStream fileStream = null;
      InputStream input = null;

      try {
        String urlToUse = resolve(row.getString(urlFieldnameToUse, ""));
        String realUploadFile = resolve(row.getString(uploadFieldnameToUse, ""));
        String realTargetFile = resolve(row.getString(destinationFieldnameToUse, ""));

        logBasic(BaseMessages.getString(PKG, "ActionHTTP.Log.ConnectingURL", urlToUse));

        if (!Utils.isEmpty(proxyHostname)) {
          System.setProperty(CONST_HTTP_PROXY_HOST, resolve(proxyHostname));
          System.setProperty(CONST_HTTP_PROXY_PORT, resolve(proxyPort));
          System.setProperty(CONST_HTTPS_PROXY_HOST, resolve(proxyHostname));
          System.setProperty(CONST_HTTPS_PROXY_PORT, resolve(proxyPort));
          if (nonProxyHosts != null) {
            System.setProperty(CONST_HTTP_NON_PROXY_HOSTS, resolve(nonProxyHosts));
          }
        }

        if (!Utils.isEmpty(username)) {
          Authenticator.setDefault(
              new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                  String realPassword = Encr.decryptPasswordOptionallyEncrypted(resolve(password));
                  return new PasswordAuthentication(
                      resolve(username),
                      realPassword != null ? realPassword.toCharArray() : new char[] {});
                }
              });
        }

        if (dateTimeAdded) {
          SimpleDateFormat daf = new SimpleDateFormat();
          Date now = new Date();

          daf.applyPattern("yyyMMdd");
          realTargetFile += "_" + daf.format(now);
          daf.applyPattern("HHmmss");
          realTargetFile += "_" + daf.format(now);

          if (!Utils.isEmpty(targetFilenameExtension)) {
            realTargetFile += "." + resolve(targetFilenameExtension);
          }
        }

        // Create the output File...
        outputFile = HopVfs.getOutputStream(realTargetFile, fileAppended);

        // Get a stream for the specified URL
        server = new URL(urlToUse);
        URLConnection connection = server.openConnection();

        if (isIgnoreSsl()) {
          HttpsURLConnection httpsConn = (HttpsURLConnection) connection;
          httpsConn.setSSLSocketFactory(
              HttpClientManager.getTrustAllSslContext().getSocketFactory());
          httpsConn.setHostnameVerifier(
              HttpClientManager.getHostnameVerifier(isDebug(), getLogChannel()));
        }

        // if we have HTTP headers, add them
        if (!Utils.isEmpty(headerName)) {
          if (isDebug()) {
            logDebug(BaseMessages.getString(PKG, "ActionHTTP.Log.HeadersProvided"));
          }
          for (int j = 0; j < headerName.length; j++) {
            if (!Utils.isEmpty(headerValue[j])) {
              connection.setRequestProperty(resolve(headerName[j]), resolve(headerValue[j]));
              if (isDebug()) {
                logDebug(
                    BaseMessages.getString(
                        PKG,
                        "ActionHTTP.Log.HeaderSet",
                        resolve(headerName[j]),
                        resolve(headerValue[j])));
              }
            }
          }
        }

        connection.setDoOutput(true);

        // See if we need to send a file over?
        if (!Utils.isEmpty(realUploadFile)) {
          if (isDetailed()) {
            logDetailed(BaseMessages.getString(PKG, "ActionHTTP.Log.SendingFile", realUploadFile));
          }

          // Grab an output stream to upload data to web server
          uploadStream = connection.getOutputStream();
          fileStream = new BufferedInputStream(new FileInputStream(new File(realUploadFile)));
          try {
            int c;
            while ((c = fileStream.read()) >= 0) {
              uploadStream.write(c);
            }
          } finally {
            // Close upload and file
            if (uploadStream != null) {
              uploadStream.close();
              uploadStream = null;
            }
            if (fileStream != null) {
              fileStream.close();
              fileStream = null;
            }
          }
          if (isDetailed()) {
            logDetailed(BaseMessages.getString(PKG, "ActionHTTP.Log.FinishedSendingFile"));
          }
        }

        if (isDetailed()) {
          logDetailed(BaseMessages.getString(PKG, "ActionHTTP.Log.StartReadingReply"));
        }

        // Read the result from the server...
        input = connection.getInputStream();
        Date date = new Date(connection.getLastModified());
        logBasic(
            BaseMessages.getString(
                PKG, "ActionHTTP.Log.ReplayInfo", connection.getContentType(), date));

        int oneChar;
        long bytesRead = 0L;
        while ((oneChar = input.read()) != -1) {
          outputFile.write(oneChar);
          bytesRead++;
        }

        logBasic(
            BaseMessages.getString(
                PKG, "ActionHTTP.Log.FinisedWritingReply", bytesRead, realTargetFile));

        if (addfilenameresult) {
          // Add to the result files...
          ResultFile resultFile =
              new ResultFile(
                  ResultFile.FILE_TYPE_GENERAL,
                  HopVfs.getFileObject(realTargetFile),
                  parentWorkflow.getWorkflowName(),
                  toString());
          result.getResultFiles().put(resultFile.getFile().toString(), resultFile);
        }

        result.setResult(true);
      } catch (MalformedURLException e) {
        result.setNrErrors(1);
        logError(BaseMessages.getString(PKG, "ActionHTTP.Error.NotValidURL", url, e.getMessage()));
        logError(Const.getStackTracker(e));
      } catch (IOException e) {
        result.setNrErrors(1);
        logError(
            BaseMessages.getString(PKG, "ActionHTTP.Error.CanNotSaveHTTPResult", e.getMessage()));
        logError(Const.getStackTracker(e));
      } catch (Exception e) {
        result.setNrErrors(1);
        logError(
            BaseMessages.getString(PKG, "ActionHTTP.Error.ErrorGettingFromHTTP", e.getMessage()));
        logError(Const.getStackTracker(e));
      } finally {
        // Close it all
        try {
          if (uploadStream != null) {
            uploadStream.close(); // just to make sure
          }
          if (fileStream != null) {
            fileStream.close(); // just to make sure
          }

          if (input != null) {
            input.close();
          }
          if (outputFile != null) {
            outputFile.close();
          }
        } catch (Exception e) {
          logError(
              BaseMessages.getString(PKG, "ActionHTTP.Error.CanNotCloseStream", e.getMessage()));
          result.setNrErrors(1);
        }

        // Set the proxy settings back as they were on the system!
        System.setProperty(CONST_HTTP_PROXY_HOST, Const.NVL(beforeProxyHost, ""));
        System.setProperty(CONST_HTTP_PROXY_PORT, Const.NVL(beforeProxyPort, ""));
        System.setProperty(CONST_HTTPS_PROXY_HOST, Const.NVL(beforeHttpsProxyHost, ""));
        System.setProperty(CONST_HTTPS_PROXY_PORT, Const.NVL(beforeHttpsProxyPort, ""));
        System.setProperty(CONST_HTTP_NON_PROXY_HOSTS, Const.NVL(beforeNonProxyHosts, ""));
      }
    }

    return result;
  }

  @Override
  public boolean isEvaluation() {
    return true;
  }

  @Override
  public List<ResourceReference> getResourceDependencies(
      IVariables variables, WorkflowMeta workflowMeta) {
    List<ResourceReference> references = super.getResourceDependencies(variables, workflowMeta);
    String realUrl = resolve(url);
    ResourceReference reference = new ResourceReference(this);
    reference.getEntries().add(new ResourceEntry(realUrl, ResourceType.URL));
    references.add(reference);
    return references;
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      WorkflowMeta workflowMeta,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    ActionValidatorUtils.andValidator()
        .validate(
            this,
            "targetFilename",
            remarks,
            AndValidator.putValidators(ActionValidatorUtils.notBlankValidator()));
    ActionValidatorUtils.andValidator()
        .validate(
            this,
            "targetFilenameExtention",
            remarks,
            AndValidator.putValidators(ActionValidatorUtils.notBlankValidator()));
    ActionValidatorUtils.andValidator()
        .validate(
            this,
            "uploadFilename",
            remarks,
            AndValidator.putValidators(ActionValidatorUtils.notBlankValidator()));
    ActionValidatorUtils.andValidator()
        .validate(
            this,
            "proxyPort",
            remarks,
            AndValidator.putValidators(ActionValidatorUtils.integerValidator()));
  }
}
