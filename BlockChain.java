import java.util.*;
import java.io.*;
import java.util.List;
import java.util.Arrays;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;
	
	private TreeNode<Block> rootNode;
	
	private List<Block> leafBlocksByAgeList = new ArrayList<Block>();
	
	private Map<Block, Integer> leavesDeptMap;
	
	private TransactionPool txPool;
	
	private Block maxHeightBlock;
	
	private Map<ByteArrayWrapper,UTXOPool> apendableBlocksUTXOs = new HashMap<ByteArrayWrapper,UTXOPool>();
	
	private int treeDept=1;

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
		this.rootNode = new TreeNode<Block>(genesisBlock);
		this.txPool = new TransactionPool();
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        List<Block> deepestNodes = getDeepestNodes();
		
		if(deepestNodes!=null && !deepestNodes.isEmpty())
		{
			for(Block leaf: leafBlocksByAgeList)
			{
				if(deepestNodes.contains(leaf))
				{
					this.maxHeightBlock=leaf;
					return leaf;
				}
			}
		}else{
			return null;
		}
		this.maxHeightBlock=null;
		return null;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
		UTXOPool pool = new UTXOPool();
		TxHandler txHandler = new TxHandler(pool);
		txHandler.handleTxs(maxHeightBlock.getTransactions().toArray(new Transaction[0]));
		return txHandler.getUTXOPool();
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return txPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
		boolean isLeaf = false;
		
		// find first where you put this block, with it's previous block's hash
		TreeNode<Block> prevNode = findBlockByHash(block.getPrevBlockHash());
		
		if(prevNode==null)
			return false;
		
		//check if its a leaf node
		if(prevNode.children == null || prevNode.children.isEmpty())
			isLeaf=true;
		else{
			// then check if one of the children of this block (previous block's child on the chain) is the same block we just received
			// meaning that this block we received is a confirmation block for the one we already put on the chain
			for(TreeNode<Block> child : prevNode.children)
				if(Arrays.equals(((Block)child.data).getHash(),block.getHash()))
					return true;
		}

		//else check if the previous block can be appended according to the cot off rule
		getMaxHeightBlock();
		int prevNodeDept=findBlockDept(rootNode, 1, ((Block)prevNode.data).getHash());
		if(prevNodeDept<=(treeDept-CUT_OFF_AGE))
			return false;
		
		// if yes, check the block validity
		// meaning validity of its transaction set
		UTXOPool previousBlockPool = apendableBlocksUTXOs.get(new ByteArrayWrapper(block.getPrevBlockHash()));
		TxHandler handler = new TxHandler(previousBlockPool);
		Transaction[] validTxs = handler.handleTxs(block.getTransactions().toArray(new Transaction[0]));
		if(validTxs.length!=block.getTransactions().size())
			return false;
		
		// if transaction set is valid, then put it on the chain tree as previous block's child node
		prevNode.addChild(block);
		
		// remove Tx set from TxPool
		for(Transaction tx : block.getTransactions())
			txPool.removeTransaction(tx.getHash());
		
		// and also put block in the leaf blocks' list and the apendableBlocksUTXOs 
		leafBlocksByAgeList.add(block);
		leafBlocksByAgeList.remove((Block)prevNode.data);
		apendableBlocksUTXOs.put(new ByteArrayWrapper(block.getHash()),handler.getUTXOPool());
		
		return true;
		
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
		UTXOPool uPool = getMaxHeightUTXOPool();
        TxHandler handler = new TxHandler(uPool);
        
        if(!txPool.getTransactions().contains(tx))
			if(handler.isValidTx(tx))
				txPool.addTransaction(tx);
    }
	
	public class TreeNode<T> { // implements Iterable<TreeNode<T>>

		T data;
		TreeNode<T> parent;
		List<TreeNode<T>> children;

		public TreeNode(T data) {
			this.data = data;
			this.children = new LinkedList<TreeNode<T>>();
		}

		public TreeNode<T> addChild(T child) {
			TreeNode<T> childNode = new TreeNode<T>(child);
			childNode.parent = this;
			this.children.add(childNode);
			return childNode;
		}
	}
	
	private List<Block> getDeepestNodes(){
		try{
			List<Block> result = new ArrayList<Block>();
			this.leavesDeptMap = new HashMap<Block,Integer>();
			
			this.treeDept = findTreeDept(this.rootNode, 1);
			for(Map.Entry<Block,Integer> leaf : this.leavesDeptMap.entrySet())
			{
				if(leaf.getValue()==this.treeDept)
					result.add(leaf.getKey());
			}
		}
		catch(NullPointerException ex){
			throw 
		}
		return result;
	}
	
	public int findTreeDept(TreeNode<Block> parent, int dept){
		List<TreeNode<Block>> children = parent.children;
		int newDept = dept+1;
		
		if(children==null || children.isEmpty())
		{
			this.leavesDeptMap.put((Block)parent.data,dept);
			return dept;
		}
		else{
			int deepestChild=newDept;
			for(TreeNode<Block> child : children)
			{
				int deptf = findTreeDept(child, newDept);
				if(deptf>deepestChild)
					deepestChild=deptf;
			}
			return deepestChild;
		}
	}
	
	public int findBlockDept(TreeNode<Block> parent, int dept, byte[] hash){
		List<TreeNode<Block>> children = parent.children;
		int newDept = dept+1;
		
		if(children==null || children.isEmpty())
		{
			return 0;
		}
		else{
			for(TreeNode<Block> child : children)
			{
				if(Arrays.equals(((Block)child.data).getHash(),hash))
					return newDept;
				else{
					int deptF = findBlockDept(child, newDept, hash);
					if(deptF!=0)
						return deptF;
				}
			}
			return 0;
		}
	}
	
	private TreeNode<Block> findBlockByHash(byte[] hash){
		
		List<TreeNode<Block>> children = this.rootNode.children;
		
		if(children==null || children.isEmpty())
		{
			return null;
		}
		else{
			for(TreeNode<Block> child : children)
			{
				if(Arrays.equals(((Block)child.data).getHash(), hash))
					return child;
				else{
					TreeNode node = findBlockByHash(((Block)child.data).getHash());
					if(node==null)
						continue;
					else;
						return node;
				}
				
			}
		}
		
		return null;
	}
}