This project implements parts of the index file organization for the database management system MINIBASE. You will use some methods of 
Buffer Manager Class which are provided to you. The test methods indicate the sequence of calls made on the provided layers as well as 
on the methods that you will be implementing.In addition to what you implement, this project is intended to show typical implementation 
of DBMS modules.

In this project, you will implement a B+ tree in which leaf level pages contain entries of the form
<key, rid of a data record> 
Multiple values of the same key are stored as separate <key, data ptr> on the leaf page. When you delete, you always delete the
first <key, data ptr> value. When you insert, you insert the current <key data ptr> as the last one. You shall implement the full
insert and a simple deletion algorithm. Your insert routine must be capable of dealing with overflows (at any level of the tree) 
by splitting pages using the algorithm discussed in the class/book. You will not consider re-distribution. 
For this project, you shall implement a naïve delete (simply remove the record without performing any merging or redistribution).

