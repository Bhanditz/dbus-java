/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.lang.reflect.Constructor;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.exceptions.MessageFormatException;
import org.freedesktop.dbus.exceptions.NotConnected;


/**
 * Error messages which can be sent over the bus.
 */
public class Error extends Message {

    private static final Logger log = Logger.getLogger(Error.class);


    Error () {}


    public Error ( String dest, String errorName, long replyserial, String sig, Object... args ) throws DBusException {
        this(null, dest, errorName, replyserial, sig, args);
    }


    public Error ( String source, String dest, String errorName, long replyserial, String sig, Object... args ) throws DBusException {
        super(Message.Endian.BIG, Message.MessageType.ERROR, (byte) 0);

        if ( null == errorName )
            throw new MessageFormatException("Must specify error name to Errors.");
        this.headers.put(Message.HeaderField.REPLY_SERIAL, replyserial);
        this.headers.put(Message.HeaderField.ERROR_NAME, errorName);

        Vector<Object> hargs = new Vector<>();
        hargs.add(new Object[] {
            Message.HeaderField.ERROR_NAME, new Object[] {
                ArgumentType.STRING_STRING, errorName
            }
        });
        hargs.add(new Object[] {
            Message.HeaderField.REPLY_SERIAL, new Object[] {
                ArgumentType.UINT32_STRING, replyserial
            }
        });

        if ( null != source ) {
            this.headers.put(Message.HeaderField.SENDER, source);
            hargs.add(new Object[] {
                Message.HeaderField.SENDER, new Object[] {
                    ArgumentType.STRING_STRING, source
                }
            });
        }

        if ( null != dest ) {
            this.headers.put(Message.HeaderField.DESTINATION, dest);
            hargs.add(new Object[] {
                Message.HeaderField.DESTINATION, new Object[] {
                    ArgumentType.STRING_STRING, dest
                }
            });
        }

        if ( null != sig ) {
            hargs.add(new Object[] {
                Message.HeaderField.SIGNATURE, new Object[] {
                    ArgumentType.SIGNATURE_STRING, sig
                }
            });
            this.headers.put(Message.HeaderField.SIGNATURE, sig);
            setArgs(args);
        }

        byte[] blen = new byte[4];
        appendBytes(blen);
        append("ua(yv)", this.serial, hargs.toArray());
        pad((byte) 8);

        long c = this.bytecounter;
        if ( null != sig )
            append(sig, args);
        marshallint(this.bytecounter - c, blen, 0, 4);
    }


    public Error ( String source, Message m, Throwable e ) throws DBusException {
        this(source, m.getSource(), AbstractConnection.dollar_pattern.matcher(e.getClass().getName()).replaceAll("."), m.getSerial(), "s", e
                .getMessage());
    }


    public Error ( Message m, Throwable e ) throws DBusException {
        this(m.getSource(), AbstractConnection.dollar_pattern.matcher(e.getClass().getName()).replaceAll("."), m.getSerial(), "s", e.getMessage());
    }


    @SuppressWarnings ( "unchecked" )
    private static Class<? extends DBusExecutionException> createExceptionClass ( AbstractConnection conn, String name ) {
        if ( name == "org.freedesktop.DBus.Local.Disconnected" )
            return NotConnected.class;
        Class<? extends DBusExecutionException> c = null;
        String tmpName = name;
        if ( tmpName.startsWith("org.freedesktop.DBus.") ) {
            tmpName = "org.freedesktop.dbus.DBus." + tmpName.substring("org.freedesktop.DBus.".length());
        }
        do {
            try {
                c = (Class<? extends org.freedesktop.dbus.exceptions.DBusExecutionException>) conn.loadClass(tmpName);
            }
            catch ( ClassNotFoundException CNFe ) {
                log.debug("Exception class not found " + tmpName, CNFe);
            }
            tmpName = tmpName.replaceAll("\\.([^\\.]*)$", "\\$$1");
        }
        while ( null == c && tmpName.matches(".*\\..*") );
        return c;
    }


    /**
     * Turns this into an exception of the correct type
     */
    public DBusExecutionException getException ( AbstractConnection conn ) {
        try {
            Class<? extends DBusExecutionException> c = createExceptionClass(conn, getName());
            if ( null == c || !DBusExecutionException.class.isAssignableFrom(c) )
                c = DBusExecutionException.class;
            Constructor<? extends DBusExecutionException> con = c.getConstructor(String.class);
            DBusExecutionException ex;
            Object[] args = getParameters();
            if ( null == args || 0 == args.length )
                ex = con.newInstance("");
            else {
                String s = "";
                for ( Object o : args )
                    s += o + " ";
                ex = con.newInstance(s.trim());
            }
            ex.setType(getName());
            return ex;
        }
        catch ( Exception e ) {
            log.warn("Failed to build exception", e);
            DBusExecutionException ex;
            Object[] args = null;
            try {
                args = getParameters();
            }
            catch ( Exception ee ) {
                log.debug("Exception getting parameters", ee);
            }
            if ( null == args || 0 == args.length )
                ex = new DBusExecutionException("");
            else {
                String s = "";
                for ( Object o : args )
                    s += o + " ";
                ex = new DBusExecutionException(s.trim());
            }
            ex.setType(getName());
            return ex;
        }
    }


    /**
     * Throw this as an exception of the correct type
     */
    public void throwException ( AbstractConnection conn ) throws DBusExecutionException {
        throw getException(conn);
    }
}
