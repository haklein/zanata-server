package org.fedorahosted.flies.rest.service;

import java.net.URI;
import java.net.URISyntaxException;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.fedorahosted.flies.core.dao.DocumentDAO;
import org.fedorahosted.flies.core.dao.ProjectContainerDAO;
import org.fedorahosted.flies.core.dao.ProjectDAO;
import org.fedorahosted.flies.core.dao.ProjectIterationDAO;
import org.fedorahosted.flies.core.model.HProjectIteration;
import org.fedorahosted.flies.repository.model.HDocument;
import org.fedorahosted.flies.repository.model.HProjectContainer;
import org.fedorahosted.flies.repository.model.HResource;
import org.fedorahosted.flies.rest.MediaTypes;
import org.fedorahosted.flies.rest.dto.Container;
import org.fedorahosted.flies.rest.dto.Document;
import org.fedorahosted.flies.rest.dto.DocumentView;
import org.fedorahosted.flies.rest.dto.Resource;
import org.fedorahosted.flies.rest.dto.ResourceList;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.jboss.resteasy.spi.NotFoundException;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.security.Restrict;

@Name("documentService")
@Path("/projects/p/{projectSlug}/iterations/i/{iterationSlug}/documents/d/{documentId}")
public class DocumentService {
	
	@PathParam("projectSlug")
	private String projectSlug;
	
	@PathParam("iterationSlug")
	private String iterationSlug;

	@PathParam("documentId")
	private String documentId;
	
	@In
	DocumentDAO documentDAO;
	
	@In
	ProjectContainerDAO projectContainerDAO;
	
	@In
	Session session;
	
	@GET
	@Produces({ MediaTypes.APPLICATION_FLIES_DOCUMENT_XML, MediaType.APPLICATION_JSON })
	public DocumentView get(
			final @QueryParam("languages") @DefaultValue("") ContentQualifier languages) {
		
		HProjectContainer hProjectContainer = getContainerOrFail();
		
		String docId = convertToRealDocumentId(documentId);
		
		HDocument hDoc = documentDAO.getByDocId(hProjectContainer, docId);
		
		if(hDoc == null) {
			throw new WebApplicationException(Status.NOT_FOUND);
		}
		
		return new DocumentView(null);
	}

	@PUT
	@Consumes({ MediaTypes.APPLICATION_FLIES_DOCUMENT_XML, MediaType.APPLICATION_JSON })
	@Restrict("#{identity.loggedIn}")
	public Response put(Document document) throws URISyntaxException {
		
		String hDocId = convertToRealDocumentId(documentId);

		if(!document.getId().equals(hDocId)){
			Response.notAcceptable(null).build();
		}

		HProjectContainer hProjectContainer = getContainerOrFail();

		HDocument hDoc = documentDAO.getByDocId(hProjectContainer, hDocId);
		
		if(hDoc == null) { // it's a create operation
			hDoc = new HDocument(document);
			hProjectContainer.getDocuments().add(hDoc);
			try{
				session.flush();
				for(Resource res : document.getResources()) {
					HResource hRes = HDocument.create(res);
					hRes.setDocument(hDoc);
					hDoc.getResourceTree().add(hRes);
					session.flush();
				}
				return Response.created( new URI("/d/"+hDoc.getDocId())).build();
			}
			catch(Exception e){
				return Response.notAcceptable(null).build();
			}
		}
		else{ // it's an update operation
			if(!document.getName().equals(hDoc.getName()) ){
				hDoc.setName(document.getName());
			}
			try{
				session.flush();
				return Response.ok().build();
			}
			catch(Exception e){
				return Response.notAcceptable(null).build();
			}
		}
		
	}

	@GET
	@Path("content/{qualifier}")
	public ResourceList getContent(
			@PathParam("qualifier") ContentQualifier qualifier,
			@QueryParam("levels") @DefaultValue("1") int levels){
		return null;
	}

	@POST
	@Path("content/{qualifier}")
	@Restrict("#{identity.loggedIn}")
	public Response postContent(
			ResourceList content,
			@PathParam("qualifier") ContentQualifier qualifier) {
		
		return Response.ok().build();
	}
	
	@PUT
	@Path("content/{qualifier}")
	public Response putContent(
			ResourceList content,
			@PathParam("qualifier") ContentQualifier qualifier) {
		
		return Response.ok().build();
	}

	@POST
	@Path("content/{qualifier}/{resourceId}")
	@Restrict("#{identity.loggedIn}")
	public Response postContentByResourceId(
			Resource resource,
			@PathParam("qualifier") ContentQualifier qualifier,
			@PathParam("resourceId") String resourceId) {
		
		return Response.ok().build();
	}
	
	@GET
	@Path("content/{qualifier}/{resourceId}")
	public Resource getContentByResourceId(
			@PathParam("qualifier") ContentQualifier qualifier,
			@PathParam("resourceId") String resourceId,
			@QueryParam("levels") @DefaultValue("1") int levels){
		return new Container("id");
	}
	
	private HProjectContainer getContainerOrFail(){
		HProjectContainer hProjectContainer = projectContainerDAO.getBySlug(projectSlug, iterationSlug); 
		
		if(hProjectContainer == null)
			throw new NotFoundException("Project Container not found");
		
		return hProjectContainer;
	}

	private String convertToRealDocumentId(String uriId){
		return uriId.replace(',', '/');
	}
	
}
