/**
 * =====================================================================
 * Project: PDF Extract Form xTractor
 *
 * History (only for major revisions):
 * Date         Author        	Reason for revision
 * 2012-03-18   Youssef Nokta   Creation
 * 2012-10-24   J. CHADET    	Migration to xCP 2.0
 * 
 * @com.emc.xcelerator.pdfforms;
 * =====================================================================
 * Copyright (c) 2012 EMC
 * =====================================================================
 */

package com.emc.xcp.xcelerator.pdfformxtractor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.documentum.fc.client.DfSingleDocbaseModule;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSysObject;
import com.documentum.fc.client.IDfType;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfId;
import com.documentum.fc.common.DfLogger;
import com.documentum.fc.common.DfTime;
import com.documentum.fc.common.IDfAttr;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.XfaForm;

public class ExtractFormData extends DfSingleDocbaseModule
{

	private void setAttributeValue(IDfSession session, String objectId, String attrName, String attrValue,String pDateFormat) {
		DfLogger.debug(this, "setAttributeValue called", null, null);
		try
		{
			if ((!attrValue.equals("")) && (attrValue != null) && (!attrValue.equals("null")))
			{
				IDfSysObject formObj = (IDfSysObject)session.getObject(new DfId(objectId));

				IDfType iType = (IDfType)session.getObjectByQualification("dm_type where name = '" + formObj.getTypeName() + "'");
				for (int i = 0; i < iType.getTypeAttrCount(); i++)
				{
					IDfAttr attr = iType.getTypeAttr(i);
					if (!attr.getName().equalsIgnoreCase(attrName)) {
						continue;
					}
					if (attr.getDataType() == 2) {
						formObj.setString(attrName, attrValue); break;
					}

					if (attr.getDataType() == 5) {
						double dblVal = Double.parseDouble(attrValue);
						formObj.setDouble(attrName, dblVal); break;
					}

					if (attr.getDataType() == 0) {
						boolean bVal = Boolean.parseBoolean(attrValue);
						formObj.setBoolean(attrName, bVal); break;
					}

					if (attr.getDataType() == 1) {
						int intVal = Integer.parseInt(attrValue);
						formObj.setInt(attrName, intVal); break;
					}

					if (attr.getDataType() != 4) break;
					try {
						String dateString = attrValue;
						SimpleDateFormat simple = new SimpleDateFormat(pDateFormat);
						Date date = simple.parse(dateString);
						DfTime dVal = new DfTime(date);
						formObj.setTime(attrName, dVal);
					}
					catch (ParseException pe) {
						pe.printStackTrace();
					}

				}

			}

		}
		catch (DfException dfe)
		{
			DfLogger.error(this, "Error in setAttributeValue()", null, dfe);
			dfe.printStackTrace();
		}
	}

	public void readFormData( String pObjectId, String pRootElement, String pDateFormat) throws TransformerFactoryConfigurationError, ParserConfigurationException, SAXException, TransformerException
	{
		
		DfLogger.debug(this, "readFormData called", null, null);
		try
		{
			IDfSession session = getSession();
			IDfSysObject formObj  = (IDfSysObject)session.getObject(new DfId(pObjectId));

			if (formObj.getFormat().getName().equalsIgnoreCase("pdf")) {
				InputStream fis = formObj.getContent();
				IDfCollection col = null;
				try
				{
					FileOutputStream os = new FileOutputStream(formObj.getObjectId().toString() + ".xml");
					PdfReader reader = new PdfReader(fis);
					XfaForm xfa = new XfaForm(reader);
					Node node = xfa.getDatasetsNode();
					NodeList list = node.getChildNodes();
					for (int i = 0; i < list.getLength(); i++) {
						if ("data".equals(list.item(i).getLocalName())) {
							node = list.item(i);
							break;
						}
					}

					list = node.getChildNodes();
					for (int i = 0; i < list.getLength(); i++) {
						if (list.item(i).getLocalName().equals(pRootElement)) {
							node = list.item(i);
							NodeList nl = node.getChildNodes();
							for (int n = 0; i < nl.getLength(); n++) {
								Node child = nl.item(n);
								if (child.getLocalName().equals(formObj.getTypeName())) {
									NodeList nl2 = child.getChildNodes();

									for (int j = 0; j < nl2.getLength(); j++) {
										Node child2 = nl2.item(j);
										DfLogger.debug(this, "founpd attribute = " + child2.getNodeName(), null, null);
										DfLogger.debug(this, "attribute value = " + child2.getTextContent(), null, null);
										setAttributeValue(session, pObjectId, child2.getNodeName(), child2.getTextContent(),pDateFormat);
									}
									break;
								}
							}

							break;
						}
					}
					Transformer tf = TransformerFactory.newInstance().newTransformer();
					tf.setOutputProperty("encoding", "UTF-8");
					tf.setOutputProperty("indent", "yes");
					tf.transform(new DOMSource(node), new StreamResult(os));
					reader.close();

					col = formObj.getRenditions("full_format");
					while (col.next()) {
						if (col.getString("full_format").equalsIgnoreCase("xml")) {
							formObj.removeRendition("xml");
						}
					}
					formObj.addRendition(formObj.getObjectId().toString() + ".xml", "xml");
					formObj.save();
					DfLogger.debug(this, "rendition added successfully", null, null);
					os.close();
				}
				catch (IOException e)
				{
					DfLogger.error(this, "Error in readFormData()", null, e);
					e.printStackTrace();
				}finally {
					if(col!=null){
						col.close();
					}
				}

			}
		}
		catch (DfException e)
		{
			DfLogger.error(this, "Error in readFormData()", null, e);
			e.printStackTrace();
		}
	}
}