package galileo.resource;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import galileo.comm.Connector;
import galileo.event.Event;
import galileo.net.NetworkDestination;
import galileo.service.ColumbusServerApplication;

public class ColumbusServerResource extends ServerResource {

	private static final Logger LOGGER = Logger.getLogger(ColumbusServerResource.class.getName());
	private NetworkDestination destination;
	private Connector connector;

	public ColumbusServerResource() {
		setNegotiated(false);
	}

	/**
	 * Implements the functionality that needs to be executed for every incoming
	 * request
	 */
	@Override
	protected void doInit() throws ResourceException {
		try {
			super.doInit();
			ColumbusServerApplication application = (ColumbusServerApplication) getApplication();
			this.connector = new Connector();
			this.destination = application.getDestination();
			LOGGER.info("Galileo Host: " + destination.getHostname() + ":" + destination.getPort());
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "failed to initialize server resource.", e);
			throw new ResourceException(e);
		}
	}

	@Override
	protected void doRelease() throws ResourceException {
		super.doRelease();
		LOGGER.fine("closing connector");
		this.connector.close();
	}

	public Event sendMessage(Event request) throws IOException, InterruptedException {
		return this.connector.sendMessage(this.destination, request);
	}
	
	public void publishEvent(Event request) throws IOException, InterruptedException {
		this.connector.publishEvent(this.destination, request);
	}
}