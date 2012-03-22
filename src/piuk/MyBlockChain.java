/*
 * Copyright 2011-2012 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package piuk;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONValue;

import piuk.MyTransaction.MyTransactionOutput;
import piuk.RemoteMyWallet.MyTransactionInput;


import com.google.bitcoin.bouncycastle.util.encoders.Hex;
import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.WalletTransaction;
import com.google.bitcoin.store.BlockStoreException;

import de.roderick.weberknecht.WebSocket;
import de.roderick.weberknecht.WebSocketConnection;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketException;
import de.roderick.weberknecht.WebSocketMessage;

public class MyBlockChain extends BlockChain implements WebSocketEventHandler {
	final String URL = "ws://api.blockchain.info:8335/inv";
	int nfailures = 0;
	WebSocket _websocket;
	RemoteMyWallet remoteWallet;
	StoredBlock latestBlock;

	@Override
	public int getBestChainHeight() {
		return getChainHead().getHeight();
	}

	@Override
	public StoredBlock getChainHead() {
		return latestBlock;
	}

	public MyBlockChain(NetworkParameters params, RemoteMyWallet remoteWallet) throws BlockStoreException, WebSocketException, URISyntaxException {
		super(params, remoteWallet.getBitcoinJWallet(), null);

		this._websocket = new WebSocketConnection(new URI(URL));

		this._websocket.setEventHandler(this);

		this.remoteWallet = remoteWallet;
	}

	public synchronized void addWallet(Wallet wallet) {
		throw new Error("Not supported");
	}

	public synchronized void subscribe() {
		try {
			_websocket.send("{\"op\":\"blocks_sub\"}");

			for (ECKey key : this.remoteWallet.getBitcoinJWallet().keychain) {
				_websocket.send("{\"op\":\"addr_sub\", \"addr\":\""+key.toAddress(NetworkParameters.prodNet()).toString()+"\"}");
			}
		} catch (WebSocketException e) {
			e.printStackTrace();
		}
	}

	public void onClose() {
		// TODO Auto-generated method stub

		if (nfailures < 5) {
			try {
				++nfailures;

				_websocket.connect();
			} catch (WebSocketException e) {
				e.printStackTrace();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void onMessage(WebSocketMessage wmessage) {
		try {
			String message = wmessage.getText();

			Map<String, Object> top = (Map<String, Object>) JSONValue.parse(message);

			String op = (String) top.get("op");

			Map<String, Object> x = (Map<String, Object>) top.get("x");

			if (op.equals("block")) {

				Sha256Hash hash = new Sha256Hash(Hex.decode((String)x.get("hash")));
				int blockIndex = ((Number)x.get("blockIndex")).intValue();
				int blockHeight = ((Number)x.get("height")).intValue();
				long time = ((Number)x.get("time")).longValue();

				this.latestBlock = new StoredBlock(null, BigInteger.ZERO, blockHeight);

				List<MyTransaction> transactions = remoteWallet.getMyTransactions();
				List<Number> txIndexes = (List<Number>) x.get("txIndexes");
				for (Number txIndex : txIndexes) {
					for (MyTransaction tx : transactions) {
						if (tx.txIndex == txIndex.intValue() && tx.confidence.height != blockHeight) {
							tx.confidence.height = blockHeight;
							tx.confidence.runListeners(); 
						}
					}
				}

			} else if (op.equals("utx")) {
				WalletTransaction tx = MyTransaction.fromJSONDict(x);

				BigInteger result = BigInteger.ZERO;

				BigInteger previousBalance = remoteWallet.getBitcoinJWallet().final_balance;

				for (TransactionInput input : tx.getTransaction().getInputs()) {
					//if the input is from me subtract the value
					MyTransactionInput myinput = (MyTransactionInput) input;

					if (remoteWallet.isAddressMine(input.getFromAddress().toString())) {
						result = result.subtract(myinput.value);

						remoteWallet.getBitcoinJWallet().final_balance = remoteWallet.getBitcoinJWallet().final_balance.subtract(myinput.value);
						remoteWallet.getBitcoinJWallet().total_sent = remoteWallet.getBitcoinJWallet().total_sent.add(myinput.value);
					}
				}

				for (TransactionOutput output : tx.getTransaction().getOutputs()) {
					//if the input is from me subtract the value
					MyTransactionOutput myoutput = (MyTransactionOutput) output;

					if (remoteWallet.isAddressMine(myoutput.getToAddress().toString())) {
						result = result.add(myoutput.getValue());

						remoteWallet.getBitcoinJWallet().final_balance = remoteWallet.getBitcoinJWallet().final_balance.add(myoutput.getValue());
						remoteWallet.getBitcoinJWallet().total_received = remoteWallet.getBitcoinJWallet().total_sent.add(myoutput.getValue());
					}
				}

				MyTransaction mytx = (MyTransaction) tx.getTransaction();

				mytx.result = result;

				remoteWallet.getBitcoinJWallet().addWalletTransaction(tx);

				if (result.compareTo(BigInteger.ZERO) >= 0)
					remoteWallet.getBitcoinJWallet().invokeOnCoinsReceived(tx.getTransaction(), previousBalance, remoteWallet.getBitcoinJWallet().final_balance);
				else
					remoteWallet.getBitcoinJWallet().invokeOnCoinsSent(tx.getTransaction(), previousBalance, remoteWallet.getBitcoinJWallet().final_balance);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void onOpen() {
		nfailures = 0;
	}
}