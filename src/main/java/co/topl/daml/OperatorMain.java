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
import co.topl.daml.polys.processors.TransferRequestProcessor;
import co.topl.daml.polys.processors.SignedTransferProcessor;
import co.topl.daml.assets.processors.AssetMintingRequestProcessor;
import akka.actor.ActorSystem;
import co.topl.client.Provider;
import akka.http.javadsl.model.Uri;

import io.reactivex.Flowable;
import co.topl.daml.DamlAppContext;
import co.topl.daml.ToplContext;

public class OperatorMain {

	// constants for referring to users with access to the parties
	public static final String OPERATOR_USER = "operator";

	// application id used for sending commands
	private static final String APP_ID = "OperatorMainApp";

	private static final Logger logger = LoggerFactory.getLogger(OperatorMain.class);

	public static void main(String[] args) {
		if (args.length < 4) {
			System.err.println("Usage: HOST PORT PROJECTID APIKEY");
			System.exit(-1);
		}
		String host = args[0];
		int port = Integer.parseInt(args[1]);
		String projectId = args[2];
		String apiKey = args[3];
		DamlLedgerClient client = DamlLedgerClient.newBuilder(host, port).build();
		client.connect();
		UserManagementClient userManagementClient = client.getUserManagementClient();

		String operatorParty = userManagementClient.getUser(new GetUserRequest(OPERATOR_USER)).blockingGet().getUser()
				.getPrimaryParty().get();
		Flowable<Transaction> transactions = client.getTransactionsClient().getTransactions(
				LedgerOffset.LedgerEnd.getInstance(),
				new FiltersByParty(Collections.singletonMap(operatorParty, NoFilter.instance)), true);
		Uri uri = Uri.create("https://vertx.topl.services/valhalla/" + projectId);
		DamlAppContext damlAppContext = new DamlAppContext(APP_ID, operatorParty, client);
		ToplContext toplContext = new ToplContext(ActorSystem.create(),
				new Provider.ValhallaTestNet(uri.asScala(), apiKey));
		TransferRequestProcessor transferProcessor = new TransferRequestProcessor(damlAppContext, toplContext, 3000,
				(x, y) -> true);
		transactions.forEach(transferProcessor::processTransaction);
		SignedTransferProcessor signedTransferProcessor = new SignedTransferProcessor(damlAppContext, toplContext, 3000,
				(x, y) -> true);
		transactions.forEach(signedTransferProcessor::processTransaction);
		AssetMintingRequestProcessor assetMintingRequestProcessor = new AssetMintingRequestProcessor(damlAppContext,
				toplContext, 3000, (x, y) -> true);
		transactions.forEach(assetMintingRequestProcessor::processTransaction);
	}
}
