/*
 * Copyright (c) 2016, GoMint, BlackyPaw and geNAZt
 *
 * This code is licensed under the BSD license found in the
 * LICENSE file in the root directory of this source tree.
 */

package io.gomint.proxprox.scheduler;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author geNAZt
 * @version 1.0
 *
 * @param <T> type of Task
 */
public class TaskList<T> {
    private LongElement head;

    /**
     * Add a new Element to the tasklist
     *
     * @param key which should be used to sort the element
     * @param element which should be stored
     */
    public void add( long key, T element ) {
        TaskListNode taskListNode = new TaskListNode( element, null );

        // Check if we have a head state
        if ( this.head == null ) {
            this.head = new LongElement( key, null, taskListNode, taskListNode );
        } else {
            LongElement longElement = this.head;
            LongElement previousLongElement = null;

            // Check until we got a element with a key higher than us or we reached the end
            while ( longElement != null && longElement.getKey() < key ) {
                previousLongElement = longElement;
                longElement = longElement.getNext();
            }

            // We are at the end of the chain
            if ( longElement == null ) {
                previousLongElement.setNext( new LongElement( key, null, taskListNode, taskListNode ) );
            } else {
                // Check if we need to insert a element
                if ( longElement.getKey() != key ) {
                    LongElement newLongElement = new LongElement( key, longElement, taskListNode, taskListNode );

                    if ( previousLongElement != null ) {
                        previousLongElement.setNext( newLongElement );
                    } else {
                        // We added a new head
                        this.head = newLongElement;
                    }
                } else {
                    // We already have this key, append task
                    TaskListNode node = longElement.getTaskListTail();
                    node.setTail( taskListNode );
                    longElement.setTaskListTail( taskListNode );
                }
            }
        }
    }

    /**
     * Get the timestamp of the next scheduled execution
     *
     * @return The next timestamp of the execution
     */
    public long getNextTaskTime() {
        return this.head != null ? this.head.getKey() : Long.MAX_VALUE;
    }

    /**
     * Check if the current head key is the key we want to check against
     *
     * @param key to check against
     * @return true when the next key is the key given, false when not
     */
    public boolean checkNextKey( long key ) {
        return this.head != null && this.head.getKey() == key && this.head.getTaskListHead() != null;
    }

    /**
     * Gets the next element in this List. The Element will be removed from the list
     *
     * @return next element out of this list or null when there is none
     */
    public T getNextElement() {
        // There is nothing we can reach
        if ( this.head == null ) return null;

        // Check if we have a head node
        TaskListNode taskListNode = this.head.getTaskListHead();
        while ( this.head != null && taskListNode == null ) {
            taskListNode = this.head.getTaskListHead();
            if ( taskListNode == null ) {
                // This head is empty, remove it
                this.head = this.head.getNext();
            }
        }

        // This list has reached its end
        if ( this.head == null ) return null;

        // Extract the element
        T element = taskListNode.getCurrent();
        this.head.setTaskListHead( taskListNode.getTail() );
        while ( this.head.getTaskListHead() == null ) {
            this.head = this.head.getNext();
            if ( this.head == null ) break;
        }

        return element;
    }

    /**
     * Remove a task from the list
     *
     * @param task which should be removed
     */
    public void remove( T task ) {
        // There is nothing we can reach
        if ( this.head == null ) return;

        LongElement longElement = this.head;
        LongElement previousElement = null;
        while ( longElement != null ) {
            // Check if we have a head node
            TaskListNode taskListNode = longElement.getTaskListHead();
            TaskListNode previousNode = null;

            while ( taskListNode != null ) {
                if ( taskListNode.getCurrent().equals( task ) ) {
                    // Check if we are at the tail of the longElement
                    if ( taskListNode.getTail() == null ) {
                        if ( previousNode != null ) {
                            longElement.setTaskListTail( previousNode );
                        } else {
                            longElement.setTaskListTail( null );
                        }
                    }

                    // Chain the previous node to the next node after the removed one
                    if ( previousNode != null ) {
                        previousNode.setTail( taskListNode.getTail() );
                    } else {
                        longElement.setTaskListHead( null );
                    }
                }

                previousNode = taskListNode;
                taskListNode = taskListNode.getTail();
            }

            // Check if we left a empty longElement
            if ( longElement.getTaskListHead() == null ) {
                if ( previousElement != null ) {
                    previousElement.setNext( longElement.getNext() );
                } else {
                    this.head = null;
                }
            }

            previousElement = longElement;
            longElement = longElement.getNext();
        }
    }

    @AllArgsConstructor
    @Data
    private final class LongElement {
        private long key;
        private LongElement next;
        private TaskListNode taskListHead;
        private TaskListNode taskListTail;
    }

    @AllArgsConstructor
    @Data
    private final class TaskListNode {
        private T current;
        private TaskListNode tail;
    }
}
