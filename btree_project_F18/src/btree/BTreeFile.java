/*
 * @(#) bt.java   98/03/24
 * Copyright (c) 1998 UW.  All Rights Reserved.
 *         Author: Xiaohu Li (xioahu@cs.wisc.edu).
 *
 */

package btree;

import java.io.*;

import diskmgr.*;
import bufmgr.*;
import global.*;
import heap.*;
import btree.*;
/**
 * btfile.java This is the main definition of class BTreeFile, which derives
 * from abstract base class IndexFile. It provides an insert/delete interface.
 */
public class BTreeFile extends IndexFile implements GlobalConst {

	private final static int MAGIC0 = 1989;

	private final static String lineSep = System.getProperty("line.separator");

	private static FileOutputStream fos;
	private static DataOutputStream trace;

	/**
	 * It causes a structured trace to be written to a file. This output is used
	 * to drive a visualization tool that shows the inner workings of the b-tree
	 * during its operations.
	 *
	 * @param filename
	 *            input parameter. The trace file name
	 * @exception IOException
	 *                error from the lower layer
	 */
	public static void traceFilename(String filename) throws IOException {

		fos = new FileOutputStream(filename);
		trace = new DataOutputStream(fos);
	}

	/**
	 * Stop tracing. And close trace file.
	 *
	 * @exception IOException
	 *                error from the lower layer
	 */
	public static void destroyTrace() throws IOException {
		if (trace != null)
			trace.close();
		if (fos != null)
			fos.close();
		fos = null;
		trace = null;
	}

	private BTreeHeaderPage headerPage;
	private PageId headerPageId;
	private String dbname;

	/**
	 * Access method to data member.
	 * 
	 * @return Return a BTreeHeaderPage object that is the header page of this
	 *         btree file.
	 */
	public BTreeHeaderPage getHeaderPage() {
		return headerPage;
	}

	private PageId get_file_entry(String filename) throws GetFileEntryException {
		try {
			return SystemDefs.JavabaseDB.get_file_entry(filename);
		} catch (Exception e) {
			e.printStackTrace();
			throw new GetFileEntryException(e, "");
		}
	}

	private Page pinPage(PageId pageno) throws PinPageException {
		try {
			Page page = new Page();
			SystemDefs.JavabaseBM.pinPage(pageno, page, false/* Rdisk */);
			return page;
		} catch (Exception e) {
			e.printStackTrace();
			throw new PinPageException(e, "");
		}
	}

	private void add_file_entry(String fileName, PageId pageno)
			throws AddFileEntryException {
		try {
			SystemDefs.JavabaseDB.add_file_entry(fileName, pageno);
		} catch (Exception e) {
			e.printStackTrace();
			throw new AddFileEntryException(e, "");
		}
	}

	private void unpinPage(PageId pageno) throws UnpinPageException {
		try {
			SystemDefs.JavabaseBM.unpinPage(pageno, false /* = not DIRTY */);
		} catch (Exception e) {
			e.printStackTrace();
			throw new UnpinPageException(e, "");
		}
	}

	private void freePage(PageId pageno) throws FreePageException {
		try {
			SystemDefs.JavabaseBM.freePage(pageno);
		} catch (Exception e) {
			e.printStackTrace();
			throw new FreePageException(e, "");
		}

	}

	private void delete_file_entry(String filename)
			throws DeleteFileEntryException {
		try {
			SystemDefs.JavabaseDB.delete_file_entry(filename);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DeleteFileEntryException(e, "");
		}
	}

	private void unpinPage(PageId pageno, boolean dirty)
			throws UnpinPageException {
		try {
			SystemDefs.JavabaseBM.unpinPage(pageno, dirty);
		} catch (Exception e) {
			e.printStackTrace();
			throw new UnpinPageException(e, "");
		}
	}

	/**
	 * BTreeFile class an index file with given filename should already exist;
	 * this opens it.
	 *
	 * @param filename
	 *            the B+ tree file name. Input parameter.
	 * @exception GetFileEntryException
	 *                can not ger the file from DB
	 * @exception PinPageException
	 *                failed when pin a page
	 * @exception ConstructPageException
	 *                BT page constructor failed
	 */
	public BTreeFile(String filename) throws GetFileEntryException,
			PinPageException, ConstructPageException {

		headerPageId = get_file_entry(filename);

		headerPage = new BTreeHeaderPage(headerPageId);
		dbname = new String(filename);
		/*
		 * 
		 * - headerPageId is the PageId of this BTreeFile's header page; -
		 * headerPage, headerPageId valid and pinned - dbname contains a copy of
		 * the name of the database
		 */
	}

	/**
	 * if index file exists, open it; else create it.
	 *
	 * @param filename
	 *            file name. Input parameter.
	 * @param keytype
	 *            the type of key. Input parameter.
	 * @param keysize
	 *            the maximum size of a key. Input parameter.
	 * @param delete_fashion
	 *            full delete or naive delete. Input parameter. It is either
	 *            DeleteFashion.NAIVE_DELETE or DeleteFashion.FULL_DELETE.
	 * @exception GetFileEntryException
	 *                can not get file
	 * @exception ConstructPageException
	 *                page constructor failed
	 * @exception IOException
	 *                error from lower layer
	 * @exception AddFileEntryException
	 *                can not add file into DB
	 */
	public BTreeFile(String filename, int keytype, int keysize,
			int delete_fashion) throws GetFileEntryException,
			ConstructPageException, IOException, AddFileEntryException {

		headerPageId = get_file_entry(filename);
		if (headerPageId == null) // file not exist
		{
			headerPage = new BTreeHeaderPage();
			headerPageId = headerPage.getPageId();
			add_file_entry(filename, headerPageId);
			headerPage.set_magic0(MAGIC0);
			headerPage.set_rootId(new PageId(INVALID_PAGE));
			headerPage.set_keyType((short) keytype);
			headerPage.set_maxKeySize(keysize);
			headerPage.set_deleteFashion(delete_fashion);
			headerPage.setType(NodeType.BTHEAD);
		} else {
			headerPage = new BTreeHeaderPage(headerPageId);
		}

		dbname = new String(filename);

	}

	/**
	 * Close the B+ tree file. Unpin header page.
	 *
	 * @exception PageUnpinnedException
	 *                error from the lower layer
	 * @exception InvalidFrameNumberException
	 *                error from the lower layer
	 * @exception HashEntryNotFoundException
	 *                error from the lower layer
	 * @exception ReplacerException
	 *                error from the lower layer
	 */
	public void close() throws PageUnpinnedException,
			InvalidFrameNumberException, HashEntryNotFoundException,
			ReplacerException {
		if (headerPage != null) {
			SystemDefs.JavabaseBM.unpinPage(headerPageId, true);
			headerPage = null;
		}
	}

	/**
	 * Destroy entire B+ tree file.
	 *
	 * @exception IOException
	 *                error from the lower layer
	 * @exception IteratorException
	 *                iterator error
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception FreePageException
	 *                error when free a page
	 * @exception DeleteFileEntryException
	 *                failed when delete a file from DM
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception PinPageException
	 *                failed when pin a page
	 */
	public void destroyFile() throws IOException, IteratorException,
			UnpinPageException, FreePageException, DeleteFileEntryException,
			ConstructPageException, PinPageException {
		if (headerPage != null) {
			PageId pgId = headerPage.get_rootId();
			if (pgId.pid != INVALID_PAGE)
				_destroyFile(pgId);
			unpinPage(headerPageId);
			freePage(headerPageId);
			delete_file_entry(dbname);
			headerPage = null;
		}
	}

	private void _destroyFile(PageId pageno) throws IOException,
			IteratorException, PinPageException, ConstructPageException,
			UnpinPageException, FreePageException {

		BTSortedPage sortedPage;
		Page page = pinPage(pageno);
		sortedPage = new BTSortedPage(page, headerPage.get_keyType());

		if (sortedPage.getType() == NodeType.INDEX) {
			BTIndexPage indexPage = new BTIndexPage(page,
					headerPage.get_keyType());
			RID rid = new RID();
			PageId childId;
			KeyDataEntry entry;
			for (entry = indexPage.getFirst(rid); entry != null; entry = indexPage
					.getNext(rid)) {
				childId = ((IndexData) (entry.data)).getData();
				_destroyFile(childId);
			}
		} else { // BTLeafPage

			unpinPage(pageno);
			freePage(pageno);
		}

	}

	private void updateHeader(PageId newRoot) throws IOException,
			PinPageException, UnpinPageException {

		BTreeHeaderPage header;
		PageId old_data;

		header = new BTreeHeaderPage(pinPage(headerPageId));

		old_data = headerPage.get_rootId();
		header.set_rootId(newRoot);

		// clock in dirty bit to bm so our dtor needn't have to worry about it
		unpinPage(headerPageId, true /* = DIRTY */);

		// ASSERTIONS:
		// - headerPage, headerPageId valid, pinned and marked as dirty

	}

	/**
	 * insert record with the given key and rid
	 *
	 * @param key
	 *            the key of the record. Input parameter.
	 * @param rid
	 *            the rid of the record. Input parameter.
	 * @exception KeyTooLongException
	 *                key size exceeds the max keysize.
	 * @exception KeyNotMatchException
	 *                key is not integer key nor string key
	 * @exception IOException
	 *                error from the lower layer
	 * @exception LeafInsertRecException
	 *                insert error in leaf page
	 * @exception IndexInsertRecException
	 *                insert error in index page
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception NodeNotMatchException
	 *                node not match index page nor leaf page
	 * @exception ConvertException
	 *                error when convert between revord and byte array
	 * @exception DeleteRecException
	 *                error when delete in index page
	 * @exception IndexSearchException
	 *                error when search
	 * @exception IteratorException
	 *                iterator error
	 * @exception LeafDeleteException
	 *                error when delete in leaf page
	 * @exception InsertException
	 *                error when insert in index page
	 */
	public void insert(KeyClass key, RID rid) throws KeyTooLongException,
			KeyNotMatchException, LeafInsertRecException,
			IndexInsertRecException, ConstructPageException,
			UnpinPageException, PinPageException, NodeNotMatchException,
			ConvertException, DeleteRecException, IndexSearchException,
			IteratorException, LeafDeleteException, InsertException,
			IOException

	{
	//Check if the tree is empty or not
		if(headerPage.get_rootId().pid==INVALID_PAGE)
		{
		//creating a new root page and setting the pointers
			BTLeafPage newRootPage=new BTLeafPage(headerPage.get_keyType());
			newRootPage.setNextPage(new PageId(INVALID_PAGE));
			newRootPage.setPrevPage(new PageId(INVALID_PAGE));
			//inserting the records into the root and unpinning the page
			newRootPage.insertRecord(key,rid);
			PageId newRootPageID=newRootPage.getCurPage();
			unpinPage(newRootPageID,true);
			updateHeader(newRootPageID);
		}
			else {
			//if tree is not empty pass the value of the header to _insert method
			//to call it recursively and insert records
				PageId newRootPageID=headerPage.get_rootId();
			KeyDataEntry newRootEntry=_insert(key,rid,newRootPageID);
			//to check is spit has occured
			if(newRootEntry!=null)
		    {
			//if it has occured create a new index page 
				BTIndexPage newIndexPage=new BTIndexPage(headerPage.get_keyType());
				//insert the key
				newIndexPage.insertKey(newRootEntry.key,((IndexData)newRootEntry.data).getData());
				//set the pointers of the new index page
				newIndexPage.setPrevPage(headerPage.get_rootId());
				PageId newIndexPageID=newIndexPage.getCurPage();
				unpinPage(newIndexPageID,true);
				updateHeader(newIndexPageID);
			}
			}
		}
		 	
	

	private KeyDataEntry _insert(KeyClass key, RID rid, PageId currentPageId)
			throws PinPageException, IOException, ConstructPageException,
			LeafDeleteException, ConstructPageException, DeleteRecException,
			IndexSearchException, UnpinPageException, LeafInsertRecException,
			ConvertException, IteratorException, IndexInsertRecException,
			KeyNotMatchException, NodeNotMatchException, InsertException

	{
		Page newPage=new Page();
		//create a BTSorted Page to check what is the tpe of the Input Page
		BTSortedPage currentPage=new BTSortedPage(currentPageId,headerPage.get_keyType());
		KeyDataEntry upEntry=new KeyDataEntry(key,currentPageId) ;
		short type=11;
		//check the type of the current page
		if(currentPage.getType()==11)
		{
		//if it is am index page create an index page and pin it with the current page id
			BTIndexPage currentIndexPage=new BTIndexPage(currentPageId,headerPage.get_keyType());
			PageId currentIndexPageId=currentIndexPage.getCurPage();
			//get the next page pointed by te index page according to the given key
			PageId nextPageId=currentIndexPage.getPageNoByKey(key);
			unpinPage(currentIndexPageId,true);
			//recursively call the insert method with the next page id 
			upEntry=_insert(key,rid,nextPageId);
			//if it returns null,no spit occured 
			//no insert in the index page
			if(upEntry==null)
			{
				return null;
			}
			//else create a index page and pin it with te current index page id
			currentIndexPage=new BTIndexPage(pinPage(currentIndexPageId),headerPage.get_keyType());
			//check if space is available in the index page
			if(currentIndexPage.available_space()>=BT.getKeyDataLength(upEntry.key,NodeType.INDEX))
			{
				currentIndexPage.insertKey(upEntry.key,((IndexData)upEntry.data).getData());
				unpinPage(currentIndexPageId,true);
				return null;
			}
			else
			{
			//if space is not available a split is necessary
			//create a new Index page 
				BTIndexPage newIndexPage=new BTIndexPage(headerPage.get_keyType());
				//get he page id of the new index page
				PageId newIndexPageId=newIndexPage.getCurPage();
				//create an KeyDataEntry
				KeyDataEntry indextmpEntry;
				//create an KeyDataEntry to undo last key
				KeyDataEntry indexundoEntry=null;
				RID indexdelRID=new RID();
				int x=0;
				int y=0;
				//copy all the keys from the curent to new index page and delete all records in the current index page
				for(indextmpEntry=currentIndexPage.getFirst(indexdelRID);indextmpEntry!=null;indextmpEntry=currentIndexPage.getFirst(indexdelRID))
				{
                	  newIndexPage.insertKey(indextmpEntry.key,((IndexData)indextmpEntry.data).getData());
                	  currentIndexPage.deleteSortedRecord(indexdelRID);
				}
				RID indexdelRID2=new RID();
				//copy the first half of the keys back into the current Index Page
				for(indextmpEntry=newIndexPage.getFirst(indexdelRID2);(newIndexPage.available_space()<currentIndexPage.available_space());indextmpEntry=newIndexPage.getFirst(indexdelRID2))
				{
					indexundoEntry=indextmpEntry;
					currentIndexPage.insertKey(indextmpEntry.key,((IndexData)indextmpEntry.data).getData());
					newIndexPage.deleteSortedRecord(indexdelRID);
				}
				//check the space availbale in both index pages and insert the last key
				if(currentIndexPage.available_space()<newIndexPage.available_space())
				{
					newIndexPage.insertKey(indexundoEntry.key,((IndexData)indexundoEntry.data).getData());
					currentIndexPage.deleteSortedRecord(new RID(currentIndexPage.getCurPage(),(int)currentIndexPage.getSlotCnt()-1));
				}
				
				indexundoEntry=newIndexPage.getFirst(indexdelRID);
				//compare the keys of both index pages with the input key and insert the key in the corresponding index pahe
				if(BT.keyCompare(upEntry.key,indexundoEntry.key)>=0)
				{
					newIndexPage.insertKey(upEntry.key,((IndexData)upEntry.data).getData());
				}
				else
				{
					currentIndexPage.insertKey(upEntry.key,((IndexData)upEntry.data).getData());
				}
				//unpin the current index page
				unpinPage(currentIndexPageId,true);
				upEntry=newIndexPage.getFirst(indexdelRID);
				newIndexPage.setPrevPage(((IndexData)upEntry.data).getData());
				newIndexPage.deleteSortedRecord(indexdelRID);
			    //unpin the new index page
				unpinPage(newIndexPageId,true);
				((IndexData)upEntry.data).setData(newIndexPageId);
				return upEntry;
			}	
		
			
		}
		else if (currentPage.getType()==12)
		{
		//if the current page is of type Leaf Page
		//create a new current leaf page and pin it with current page id
			BTLeafPage currentLeafPage=new BTLeafPage(currentPageId,headerPage.get_keyType());
			PageId currentLeafPageId=currentLeafPage.getCurPage();
			//check if current leaf page has space availble for insertion
			if(currentLeafPage.available_space()>=BT.getKeyDataLength(upEntry.key,NodeType.LEAF))
			{
				currentLeafPage.insertRecord(key,rid);
				unpinPage(currentLeafPageId,true);
				return null;
			}
			else
			{
			//if space is not available means a leaf split is neccessary
			//create a new leaf apge
				BTLeafPage newLeafPage=new BTLeafPage(headerPage.get_keyType());
				//set the pointers of  both the new leaf page and the current leaf page
				PageId newLeafPageID=newLeafPage.getCurPage();
				newLeafPage.setNextPage(new PageId(INVALID_PAGE));
				newLeafPage.setPrevPage(currentLeafPageId);
				currentLeafPage.setNextPage(newLeafPageID);
				//create a keydataentry 
				KeyDataEntry tmpEntry;
				//create a keydata entry to undo the last record
				KeyDataEntry undoEntry = null;
				KeyDataEntry delEntry;
				RID delRID=new RID();
				int pagSize=newLeafPage.available_space();
				int recordSize=BT.getKeyDataLength(upEntry.key,NodeType.LEAF);
				int recordNumber=62;
				int x=0;
				int y=0;
				//copy second half of current leaf page records into new leaf page
				for(tmpEntry=currentLeafPage.getFirst(delRID);tmpEntry!=null;tmpEntry=currentLeafPage.getNext(delRID))
				{
                  if(x>=((recordNumber)/2))
                  {   
                	  newLeafPage.insertRecord(tmpEntry.key,((LeafData)tmpEntry.data).getData());
                	y=y+1;
                  }
                  else
                  {
                	  undoEntry=tmpEntry;
                  }
                  x=x+1;
				}
				tmpEntry=currentLeafPage.getFirst(delRID);
				//delete the second half of the records from current leaf page
				for(int i=1;i<=x;i++)
				{
					tmpEntry=currentLeafPage.getCurrent(delRID);
					if(i>(x/2))
					{
						currentLeafPage.deleteSortedRecord(delRID);
						tmpEntry=currentLeafPage.getCurrent(delRID);
					}
					else
					{
						tmpEntry=currentLeafPage.getNext(delRID);
					}
				}
				//compare the keys of key and undoentry to decide in which leaf page the new record has to be inserted
				if(BT.keyCompare(key,undoEntry.key)>0)
				{
					newLeafPage.insertRecord(key,rid);
				}
				else
				{
					currentLeafPage.insertRecord(key,rid);
				}
				//unpin the current leaf page
				unpinPage(currentLeafPageId,true);
				tmpEntry=newLeafPage.getFirst(delRID);
				upEntry=new KeyDataEntry(tmpEntry.key,newLeafPageID);
				//unpin the new leaf page
				unpinPage(newLeafPageID,true);
				return upEntry;
			
		}
		}
		else {
			throw new InsertException(null,"");
		}
	}

	



	/**
	 * delete leaf entry given its <key, rid> pair. `rid' is IN the data entry;
	 * it is not the id of the data entry)
	 *
	 * @param key
	 *            the key in pair <key, rid>. Input Parameter.
	 * @param rid
	 *            the rid in pair <key, rid>. Input Parameter.
	 * @return true if deleted. false if no such record.
	 * @exception DeleteFashionException
	 *                neither full delete nor naive delete
	 * @exception LeafRedistributeException
	 *                redistribution error in leaf pages
	 * @exception RedistributeException
	 *                redistribution error in index pages
	 * @exception InsertRecException
	 *                error when insert in index page
	 * @exception KeyNotMatchException
	 *                key is neither integer key nor string key
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception IndexInsertRecException
	 *                error when insert in index page
	 * @exception FreePageException
	 *                error in BT page constructor
	 * @exception RecordNotFoundException
	 *                error delete a record in a BT page
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception IndexFullDeleteException
	 *                fill delete error
	 * @exception LeafDeleteException
	 *                delete error in leaf page
	 * @exception IteratorException
	 *                iterator error
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception DeleteRecException
	 *                error when delete in index page
	 * @exception IndexSearchException
	 *                error in search in index pages
	 * @exception IOException
	 *                error from the lower layer
	 *
	 */
	public boolean Delete(KeyClass key, RID rid) throws DeleteFashionException,
			LeafRedistributeException, RedistributeException,
			InsertRecException, KeyNotMatchException, UnpinPageException,
			IndexInsertRecException, FreePageException,
			RecordNotFoundException, PinPageException,
			IndexFullDeleteException, LeafDeleteException, IteratorException,
			ConstructPageException, DeleteRecException, IndexSearchException,
			IOException {
		if (headerPage.get_deleteFashion() == DeleteFashion.NAIVE_DELETE)
			return NaiveDelete(key, rid);
		else
			throw new DeleteFashionException(null, "");
	}

	/*
	 * findRunStart. Status BTreeFile::findRunStart (const void lo_key, RID
	 * *pstartrid)
	 * 
	 * find left-most occurrence of `lo_key', going all the way left if lo_key
	 * is null.
	 * 
	 * Starting record returned in *pstartrid, on page *pppage, which is pinned.
	 * 
	 * Since we allow duplicates, this must "go left" as described in the text
	 * (for the search algorithm).
	 * 
	 * @param lo_key find left-most occurrence of `lo_key', going all the way
	 * left if lo_key is null.
	 * 
	 * @param startrid it will reurn the first rid =< lo_key
	 * 
	 * @return return a BTLeafPage instance which is pinned. null if no key was
	 * found.
	 */

	BTLeafPage findRunStart(KeyClass lo_key, RID startrid) throws IOException,
			IteratorException, KeyNotMatchException, ConstructPageException,
			PinPageException, UnpinPageException {
		BTLeafPage pageLeaf;
		BTIndexPage pageIndex;
		Page page;
		BTSortedPage sortPage;
		PageId pageno;
		PageId curpageno = null; // iterator
		PageId prevpageno;
		PageId nextpageno;
		RID curRid;
		KeyDataEntry curEntry;

		pageno = headerPage.get_rootId();

		if (pageno.pid == INVALID_PAGE) { // no pages in the BTREE
			pageLeaf = null; // should be handled by
			// startrid =INVALID_PAGEID ; // the caller
			return pageLeaf;
		}

		page = pinPage(pageno);
		sortPage = new BTSortedPage(page, headerPage.get_keyType());

		if (trace != null) {
			trace.writeBytes("VISIT node " + pageno + lineSep);
			trace.flush();
		}

		// ASSERTION
		// - pageno and sortPage is the root of the btree
		// - pageno and sortPage valid and pinned

		while (sortPage.getType() == NodeType.INDEX) {
			pageIndex = new BTIndexPage(page, headerPage.get_keyType());
			prevpageno = pageIndex.getPrevPage();
			curEntry = pageIndex.getFirst(startrid);
			while (curEntry != null && lo_key != null
					&& BT.keyCompare(curEntry.key, lo_key) < 0) {

				prevpageno = ((IndexData) curEntry.data).getData();
				curEntry = pageIndex.getNext(startrid);
			}

			unpinPage(pageno);

			pageno = prevpageno;
			page = pinPage(pageno);
			sortPage = new BTSortedPage(page, headerPage.get_keyType());

			if (trace != null) {
				trace.writeBytes("VISIT node " + pageno + lineSep);
				trace.flush();
			}

		}

		pageLeaf = new BTLeafPage(page, headerPage.get_keyType());

		curEntry = pageLeaf.getFirst(startrid);
		while (curEntry == null) {
			// skip empty leaf pages off to left
			nextpageno = pageLeaf.getNextPage();
			unpinPage(pageno);
			if (nextpageno.pid == INVALID_PAGE) {
				// oops, no more records, so set this scan to indicate this.
				return null;
			}

			pageno = nextpageno;
			pageLeaf = new BTLeafPage(pinPage(pageno), headerPage.get_keyType());
			curEntry = pageLeaf.getFirst(startrid);
		}

		// ASSERTIONS:
		// - curkey, curRid: contain the first record on the
		// current leaf page (curkey its key, cur
		// - pageLeaf, pageno valid and pinned

		if (lo_key == null) {
			return pageLeaf;
			// note that pageno/pageLeaf is still pinned;
			// scan will unpin it when done
		}

		while (BT.keyCompare(curEntry.key, lo_key) < 0) {
			curEntry = pageLeaf.getNext(startrid);
			while (curEntry == null) { // have to go right
				nextpageno = pageLeaf.getNextPage();
				unpinPage(pageno);

				if (nextpageno.pid == INVALID_PAGE) {
					return null;
				}

				pageno = nextpageno;
				pageLeaf = new BTLeafPage(pinPage(pageno),
						headerPage.get_keyType());

				curEntry = pageLeaf.getFirst(startrid);
			}
		}

		return pageLeaf;
	}

	/*
	 * Status BTreeFile::NaiveDelete (const void *key, const RID rid)
	 * 
	 * Remove specified data entry (<key, rid>) from an index.
	 * 
	 * We don't do merging or redistribution, but do allow duplicates.
	 * 
	 * Page containing first occurrence of key `key' is found for us by
	 * findRunStart. We then iterate for (just a few) pages, if necesary, to
	 * find the one containing <key,rid>, which we then delete via
	 * BTLeafPage::delUserRid.
	 */

	private boolean NaiveDelete(KeyClass key, RID rid)
			throws LeafDeleteException, KeyNotMatchException, PinPageException,
			ConstructPageException, IOException, UnpinPageException,
			PinPageException, IndexSearchException, IteratorException {
	// remove the return statement and start your code.
			//return false;
			//create a leaf page to delete the the record
		BTLeafPage leafPage=new BTLeafPage(headerPage.get_keyType());
		//create a leaf page next page to move to the next page
		BTLeafPage nextPage=new BTLeafPage(headerPage.get_keyType());
		PageId leafPageId=leafPage.getCurPage();
		PageId nextPageId;
		KeyDataEntry entry;
		int delete=0;
		RID delRID=new RID();
		RID firstRID=new RID();
		//use the findrunstart to get the  first page and rid of keys
		leafPage=findRunStart(key,delRID);
		//if the page is empty return null
		if(leafPage==null)
		{
			return false;
		}
		entry=leafPage.getCurrent(delRID);
		while(true)
		{//iterate to find the next entry in the leaf page that is not null 
		//else move to the next page to find the next non null record
			while(entry==null)
			{
				nextPageId=leafPage.getNextPage();
				unpinPage(leafPageId,true);
				if(nextPageId.pid==INVALID_PAGE)
				{
					return false;
				}
				leafPage=new BTLeafPage(pinPage(nextPageId),headerPage.get_keyType());
				entry=leafPage.getFirst(firstRID);
			}
			//if the key is greater than entry.key that means we gone ahead of all the
			//pages that could contain the key and we need to break
			if(BT.keyCompare(key,entry.key)>0)
			{
				break;
			}
			//if the record is found delete and continue untill all the records containing 
			//the same key are deleted(duplicate deletion)
			while(leafPage.delEntry(new KeyDataEntry(key,rid)))
			{
				delete=1;
			}
			//get the next page
			nextPageId=leafPage.getNextPage();
			leafPageId=leafPage.getCurPage();
			//if the next page is null break
			if(nextPageId.pid==-1)
			{
				break;
			}
			//create a new page
			leafPage=new BTLeafPage(pinPage(nextPageId),headerPage.get_keyType());
			//get the first rid
			entry=leafPage.getFirst(delRID);
			leafPageId=leafPage.getCurPage();
			//unpin the leaf page
			unpinPage(leafPageId,true);
		}
		//if a record was deleted return true
		if(delete==1)
		{
			return true;
		}
		//else return false 
		return false;
	}
	/**
	 * create a scan with given keys Cases: (1) lo_key = null, hi_key = null
	 * scan the whole index (2) lo_key = null, hi_key!= null range scan from min
	 * to the hi_key (3) lo_key!= null, hi_key = null range scan from the lo_key
	 * to max (4) lo_key!= null, hi_key!= null, lo_key = hi_key exact match (
	 * might not unique) (5) lo_key!= null, hi_key!= null, lo_key < hi_key range
	 * scan from lo_key to hi_key
	 *
	 * @param lo_key
	 *            the key where we begin scanning. Input parameter.
	 * @param hi_key
	 *            the key where we stop scanning. Input parameter.
	 * @exception IOException
	 *                error from the lower layer
	 * @exception KeyNotMatchException
	 *                key is not integer key nor string key
	 * @exception IteratorException
	 *                iterator error
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception UnpinPageException
	 *                error when unpin a page
	 */
	public BTFileScan new_scan(KeyClass lo_key, KeyClass hi_key)
			throws IOException, KeyNotMatchException, IteratorException,
			ConstructPageException, PinPageException, UnpinPageException

	{
		BTFileScan scan = new BTFileScan();
		if (headerPage.get_rootId().pid == INVALID_PAGE) {
			scan.leafPage = null;
			return scan;
		}

		scan.treeFilename = dbname;
		scan.endkey = hi_key;
		scan.didfirst = false;
		scan.deletedcurrent = false;
		scan.curRid = new RID();
		scan.keyType = headerPage.get_keyType();
		scan.maxKeysize = headerPage.get_maxKeySize();
		scan.bfile = this;

		// this sets up scan at the starting position, ready for iteration
		scan.leafPage = findRunStart(lo_key, scan.curRid);
		return scan;
	}

	void trace_children(PageId id) throws IOException, IteratorException,
			ConstructPageException, PinPageException, UnpinPageException {

		if (trace != null) {

			BTSortedPage sortedPage;
			RID metaRid = new RID();
			PageId childPageId;
			KeyClass key;
			KeyDataEntry entry;
			sortedPage = new BTSortedPage(pinPage(id), headerPage.get_keyType());

			// Now print all the child nodes of the page.
			if (sortedPage.getType() == NodeType.INDEX) {
				BTIndexPage indexPage = new BTIndexPage(sortedPage,
						headerPage.get_keyType());
				trace.writeBytes("INDEX CHILDREN " + id + " nodes" + lineSep);
				trace.writeBytes(" " + indexPage.getPrevPage());
				for (entry = indexPage.getFirst(metaRid); entry != null; entry = indexPage
						.getNext(metaRid)) {
					trace.writeBytes("   " + ((IndexData) entry.data).getData());
				}
			} else if (sortedPage.getType() == NodeType.LEAF) {
				BTLeafPage leafPage = new BTLeafPage(sortedPage,
						headerPage.get_keyType());
				trace.writeBytes("LEAF CHILDREN " + id + " nodes" + lineSep);
				for (entry = leafPage.getFirst(metaRid); entry != null; entry = leafPage
						.getNext(metaRid)) {
					trace.writeBytes("   " + entry.key + " " + entry.data);
				}
			}
			unpinPage(id);
			trace.writeBytes(lineSep);
			trace.flush();
		}

	}

}
