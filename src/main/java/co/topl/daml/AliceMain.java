package co.topl.daml;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.daml.ledger.javaapi.data.FiltersByParty;
import com.daml.ledger.javaapi.data.GetUserRequest;
import com.daml.ledger.javaapi.data.LedgerOffset;
import com.daml.ledger.javaapi.data.NoFilter;
import com.daml.ledger.javaapi.data.Transaction;
import com.daml.ledger.rxjava.DamlLedgerClient;
import com.daml.ledger.rxjava.UserManagementClient;
import co.topl.daml.polys.processors.UnsignedTransferProcessor;
import akka.actor.ActorSystem;
import co.topl.client.Provider;
import akka.http.javadsl.model.Uri;

import io.reactivex.Flowable;
import co.topl.daml.DamlAppContext;
import co.topl.daml.ToplContext;

public class AliceMain {

	// constants for referring to users with access to the parties
	public static final String ALICE_USER = "alice";

	// application id used for sending commands
	private static final String APP_ID = "AliceMainApp";

	private static final int MIN_ARG_COUNT = 4;

	private static final Logger logger = LoggerFactory.getLogger(OperatorMain.class);

	public static void main(String[] args) {
		if (args.length < MIN_ARG_COUNT) {
			System.err.println("Usage: HOST PORT PROJECTID APIKEY KEYFILENAME KEYFILEPASSWORD");
			System.exit(-1);
		}
		String host = args[0];
		int port = Integer.parseInt(args[1]);
		String keyfile = args[2];
		String password = args[3];
		DamlLedgerClient client = DamlLedgerClient.newBuilder(host, port).build();
		client.connect();
		UserManagementClient userManagementClient = client.getUserManagementClient();
		String aliceParty = userManagementClient.getUser(new GetUserRequest(ALICE_USER)).blockingGet().getUser()
				.getPrimaryParty().get();

		Flowable<Transaction> transactions = client.getTransactionsClient().getTransactions(
				LedgerOffset.LedgerEnd.getInstance(),
				new FiltersByParty(Collections.singletonMap(aliceParty, NoFilter.instance)), true);
		Uri uri = Uri.create("http://localhost:9085/");
		DamlAppContext damlAppContext = new DamlAppContext(APP_ID, aliceParty, client);
		ToplContext toplContext = new ToplContext(ActorSystem.create(), new Provider.PrivateTestNet(uri.asScala(), ""));
		UnsignedTransferProcessor unsignedTransferProcessor = new UnsignedTransferProcessor(damlAppContext, toplContext,
				keyfile, password, (x, y) -> true, t -> true);
		transactions.forEach(unsignedTransferProcessor::processTransaction);
	}
}
