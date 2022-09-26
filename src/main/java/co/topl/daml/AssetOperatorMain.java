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
import co.topl.daml.assets.processors.AssetMintingRequestProcessor;
import co.topl.daml.assets.processors.UnsignedMintingRequestProcessor;
import co.topl.daml.assets.processors.SignedMintingRequestProcessor;
import co.topl.daml.assets.processors.AssetTransferRequestProcessor;
import co.topl.daml.assets.processors.UnsignedAssetTransferRequestProcessor;
import co.topl.daml.assets.processors.SignedAssetTransferRequestProcessor;
import akka.actor.ActorSystem;
import co.topl.client.Provider;
import akka.http.javadsl.model.Uri;

import io.reactivex.Flowable;
import co.topl.daml.DamlAppContext;
import co.topl.daml.ToplContext;

public class AssetOperatorMain {

	// FIXME: Divide into smaller methods.

	// constants for referring to users with access to the parties
	public static final String OPERATOR_USER = "operator";

	// application id used for sending commands
	private static final String APP_ID = "OperatorMainApp";

	private static final Logger logger = LoggerFactory.getLogger(OperatorMain.class);

	public static void main(String[] args) {
		// FIXME: add more robust handling of parameters.
		if (args.length < 4) {
			System.err.println("Usage: HOST PORT PROJECTID  APIKEY KEYFILENAME KEYFILEPASSWORD");
			System.exit(-1);
		}
		String host = args[0];
		int port = Integer.parseInt(args[1]);
		String projectId = args[2];
		String apiKey = args[3];
		String keyfile = args[4];
		String password = args[5];
		DamlLedgerClient client = DamlLedgerClient.newBuilder(host, port).build();
		client.connect();
		UserManagementClient userManagementClient = client.getUserManagementClient();

		String operatorParty = userManagementClient.getUser(new GetUserRequest(OPERATOR_USER)).blockingGet().getUser()
				.getPrimaryParty().get();
		Flowable<Transaction> transactions = client.getTransactionsClient().getTransactions(
				LedgerOffset.LedgerEnd.getInstance(),
				new FiltersByParty(Collections.singletonMap(operatorParty, NoFilter.instance)), true);
		Uri uri = Uri.create("http://localhost:9085");
		DamlAppContext damlAppContext = new DamlAppContext(APP_ID, operatorParty, client);
		ToplContext toplContext = new ToplContext(ActorSystem.create(), new Provider.PrivateTestNet(uri.asScala(), ""));
		AssetMintingRequestProcessor assetMintingRequestProcessor = new AssetMintingRequestProcessor(damlAppContext,
				toplContext);
		transactions.forEach(assetMintingRequestProcessor::processTransaction);
		UnsignedMintingRequestProcessor unsignedMintingRequestProcessor = new UnsignedMintingRequestProcessor(
				damlAppContext, toplContext, keyfile, password);
		transactions.forEach(unsignedMintingRequestProcessor::processTransaction);
		SignedMintingRequestProcessor signedMintingRequestProcessor = new SignedMintingRequestProcessor(damlAppContext,
				toplContext);
		transactions.forEach(signedMintingRequestProcessor::processTransaction);

		AssetTransferRequestProcessor assetTransferRequestProcessor = new AssetTransferRequestProcessor(damlAppContext,
				toplContext);
		transactions.forEach(assetTransferRequestProcessor::processTransaction);
		UnsignedAssetTransferRequestProcessor unsignedTransferRequestProcessor = new UnsignedAssetTransferRequestProcessor(
				damlAppContext, toplContext, keyfile, password);
		transactions.forEach(unsignedTransferRequestProcessor::processTransaction);
		SignedAssetTransferRequestProcessor signedTransferRequestProcessor = new SignedAssetTransferRequestProcessor(
				damlAppContext, toplContext);
		transactions.forEach(signedTransferRequestProcessor::processTransaction);
	}
}
