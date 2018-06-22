public class TxHandler {
	
	private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {

		this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        
		int sumInp=0;
		int sumOut=0;
		
		
		for(Transaction.Input in : tx.getInputs())
		{
			//(1) all unsent outputs are valid
			UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
			if(!utxoPool.contains(u))
				return false;
			
			//(2) the signature of all unspent output are valid
			Crypto.verifySignature(utxoPool.getTxOutput(u).address, tx.getRawDataToSign(in.outputIndex), in.signature);
			
			//(3) no UTXO is claimed multiple times by {@code tx}
			int eqU=0;
			for(Transaction.Input otherIn : tx.getInputs())
			{
				UTXO otherU = new UTXO(otherIn.prevTxHash, otherIn.outputIndex);
				if(u.equals(otherU))
					eqU++;
			}
			if(eqU>=2)
				return false;
			
			//get the sum of all unsent transaction outputs consumed in tx
			sumInp += utxoPool.getTxOutput(u).value;   
		}
		
		//(4) all of {@code tx}s output values are non-negative
		for(Transaction.Output out : tx.getOutputs()){
			if(out.value < 0)
				return false;
			sumOut += out.value;
		}
		
		//(5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
		if(sumInp < sumOut)
			return false;
		
		return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        
		for(Transaction tx : possibleTxs)
		{
			// first check validity of tx
			// the validity also checks if, in case of multiple spending,
			// the coins claimed as input in one tx are not already assigned to another tx
			// if the UTXOs claimed are not in the UTXOPool that means they are already spent.
			if(isValidTx(tx)){
				
				//compute hash
				tx.finalize();
				
				// if yes, first remove UTXOs corresponding to inputs of tx from UTXOPool
				// to prevent multiple spending				
				for(Transaction.Input in : tx.getInputs())
				{
					UTXO u = new UTXO(in.prevTxHash, in.outputIndex);
					utxoPool.removeUTXO(u);
				}
				
				// then assigne those coins to users and put tx in the blockchain
				// meaning: get tx outputs and put cooresponding UTXOs in the current UTXOPool
				for(int i=0; i< tx.getOutputs().size(); i++)
				{
					UTXO utxo = new UTXO(tx.getHash(), i);
					utxoPool.addUTXO(utxo, tx.getOutputs().get(i));
				}
			}
		}
    }

}
