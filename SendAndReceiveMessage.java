import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.QueueClient;
import com.microsoft.azure.servicebus.ReceiveMode;
import com.microsoft.azure.servicebus.primitives.ConnectionStringBuilder;

public class SendAndReceiveMessage {

	static final String CONNECTIONSTRING = <ENDPOINT_URL>;
	static final Gson GSON = new Gson();
	static final String QUEUE_NAME = <QUEUE_NAME>;

	public void run(String connectionString) throws Exception {

		// Create a QueueClient instance for receiving using the connection string
		// builder
		// We set the receive mode to "PeekLock", meaning the message is delivered
		// under a lock and must be acknowledged ("completed") to be removed from the
		// queue
		QueueClient receiveClient = new QueueClient(new ConnectionStringBuilder(connectionString, QUEUE_NAME),
				ReceiveMode.PEEKLOCK);
		// We are using single thread executor as we are only processing one message at
		// a time
		this.registerReceiver(receiveClient);

		// Create a QueueClient instance for sending and then asynchronously send
		// messages.
		// Close the sender once the send operation is complete.
		QueueClient sendClient = new QueueClient(new ConnectionStringBuilder(connectionString, QUEUE_NAME),
				ReceiveMode.PEEKLOCK);
		this.sendMessagesAsync(sendClient).thenRunAsync(() -> sendClient.closeAsync());

		// wait for ENTER or 10 seconds elapsing
		waitForEnter(10);

		// shut down receiver to close the receive loop
		receiveClient.close();
	}

	@SuppressWarnings("rawtypes")
	CompletableFuture<Void> sendMessagesAsync(QueueClient sendClient) {
		List<HashMap<String, String>> data = GSON.fromJson(
				"[" + "{'lastName' = 'Kumbhar', 'firstName' = 'Deepak'},"
						+ "{'lastName' = 'Kale', 'firstName' = 'Yuraj'}" + "]",
				new TypeToken<List<HashMap<String, String>>>() {
				}.getType());

		List<CompletableFuture> tasks = new ArrayList<>();
		for (int i = 0; i < data.size(); i++) {
			final String messageId = Integer.toString(i);
			Message message = new Message(GSON.toJson(data.get(i), Map.class).getBytes(UTF_8));
			message.setContentType("application/json");
			message.setLabel("UserDetails");
			message.setMessageId(messageId);
			message.setTimeToLive(Duration.ofMinutes(2));
			System.out.printf("\nMessage sending: Id = %s", message.getMessageId());
			tasks.add(sendClient.sendAsync(message).thenRunAsync(() -> {
				System.out.printf("\n\tMessage acknowledged: Id = %s", message.getMessageId());
			}));
		}
		return CompletableFuture.allOf(tasks.toArray(new CompletableFuture<?>[tasks.size()]));
	}

	
	void registerReceiver(QueueClient queueClient) throws Exception {

		// register the RegisterMessageHandler callback with executor service
		queueClient.registerMessageHandler(new IMessageHandler() {
			// callback invoked when the message handler loop has obtained a message
			public CompletableFuture<Void> onMessageAsync(IMessage message) {
				// receives message is passed to callback
				if (message.getLabel() != null && message.getContentType() != null
						&& message.getLabel().contentEquals("UserDetails")
						&& message.getContentType().contentEquals("application/json")) {

					byte[] body = message.getBody();
					Map userDetails = GSON.fromJson(new String(body, UTF_8), Map.class);

					System.out.printf(
							"\n\t\t\t\tMessage received: \n\t\t\t\t\t\tMessageId = %s, \n\t\t\t\t\t\tSequenceNumber = %s, \n\t\t\t\t\t\tEnqueuedTimeUtc = %s,"
									+ "\n\t\t\t\t\t\tExpiresAtUtc = %s, \n\t\t\t\t\t\tContentType = \"%s\",  \n\t\t\t\t\t\tContent: [ firstName = %s, lastName = %s ]\n",
							message.getMessageId(), message.getSequenceNumber(), message.getEnqueuedTimeUtc(),
							message.getExpiresAtUtc(), message.getContentType(),
							userDetails != null ? userDetails.get("firstName") : "",
							userDetails != null ? userDetails.get("lastName") : "");
				}
				return CompletableFuture.completedFuture(null);
			}

			// callback invoked when the message handler has an exception to report
			public void notifyException(Throwable throwable, ExceptionPhase exceptionPhase) {
				System.out.printf(exceptionPhase + "-" + throwable.getMessage());
			}
		});

	}

	public static void main(String[] args) {

		System.exit(runApp(args, (connectionString) -> {
			SendAndReceiveMessage app = new SendAndReceiveMessage();
			try {
				app.run(connectionString);
				return 0;
			} catch (Exception e) {
				System.out.printf("%s", e.toString());
				return 1;
			}
		}));
	}

	public static int runApp(String[] args, Function<String, Integer> run) {
		try {

			String connectionString = CONNECTIONSTRING;

			// parse connection string from command line
			Options options = new Options();
			options.addOption(new Option("c", true, "Connection string"));
			CommandLineParser clp = new DefaultParser();
			CommandLine cl = clp.parse(options, args);
			if (cl.getOptionValue("c") != null) {
				connectionString = cl.getOptionValue("c");
			}

			// get overrides from the environment
			String env = System.getenv(CONNECTIONSTRING);
			if (env != null) {
				connectionString = env;
			}

			if (connectionString == null) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("run jar with", "", options, "", true);
				return 2;
			}
			return run.apply(connectionString);
		} catch (Exception e) {
			System.out.printf("%s", e.toString());
			return 3;
		}
	}

	private void waitForEnter(int seconds) {
		ExecutorService executor = Executors.newCachedThreadPool();
		try {
			executor.invokeAny(Arrays.asList(() -> {
				System.in.read();
				return 0;
			}, () -> {
				Thread.sleep(seconds * 1000);
				return 0;
			}));
		} catch (Exception e) {
			// absorb
		}
	}
}
