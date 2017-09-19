/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
 */
package org.freedesktop.dbus;


import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.DBusListType;
import org.freedesktop.dbus.types.DBusMap;
import org.freedesktop.dbus.types.DBusMapType;
import org.freedesktop.dbus.types.DBusStructType;
import org.freedesktop.dbus.types.Struct;
import org.freedesktop.dbus.types.Tuple;
import org.freedesktop.dbus.types.UInt16;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.UInt64;
import org.freedesktop.dbus.types.UnixFD;
import org.freedesktop.dbus.types.Variant;


/**
 * Contains static methods for marshalling values.
 */
public class Marshalling {

    private static final Logger log = Logger.getLogger(Marshalling.class);

    private static Map<Type, String[]> typeCache = new HashMap<>();


    /**
     * Will return the DBus type corresponding to the given Java type.
     * Note, container type should have their ParameterizedType not their
     * Class passed in here.
     * 
     * @param c
     *            The Java types.
     * @return The DBus types.
     * @throws DBusException
     *             If the given type cannot be converted to a DBus type.
     */
    public static String getDBusType ( Type[] c ) throws DBusException {
        StringBuffer sb = new StringBuffer();
        for ( Type t : c )
            for ( String s : getDBusType(t) )
                sb.append(s);
        return sb.toString();
    }


    /**
     * Will return the DBus type corresponding to the given Java type.
     * Note, container type should have their ParameterizedType not their
     * Class passed in here.
     * 
     * @param c
     *            The Java type.
     * @return The DBus type.
     * @throws DBusException
     *             If the given type cannot be converted to a DBus type.
     */
    public static String[] getDBusType ( Type c ) throws DBusException {
        String[] cached = typeCache.get(c);
        if ( null != cached )
            return cached;
        cached = getDBusType(c, false);
        typeCache.put(c, cached);
        return cached;
    }


    /**
     * Will return the DBus type corresponding to the given Java type.
     * Note, container type should have their ParameterizedType not their
     * Class passed in here.
     * 
     * @param c
     *            The Java type.
     * @param basic
     *            If true enforces this to be a non-compound type. (compound types are Maps, Structs and Lists/arrays).
     * @return The DBus type.
     * @throws DBusException
     *             If the given type cannot be converted to a DBus type.
     */
    public static String[] getDBusType ( Type c, boolean basic ) throws DBusException {
        return recursiveGetDBusType(c, basic, 0);
    }

    private static StringBuffer[] out = new StringBuffer[10];


    @SuppressWarnings ( "unchecked" )
    public static String[] recursiveGetDBusType ( Type c, boolean basic, int level ) throws DBusException {
        if ( out.length <= level ) {
            StringBuffer[] newout = new StringBuffer[out.length];
            System.arraycopy(out, 0, newout, 0, out.length);
            out = newout;
        }
        if ( null == out[ level ] )
            out[ level ] = new StringBuffer();
        else
            out[ level ].delete(0, out[ level ].length());

        if ( basic && ! ( c instanceof Class ) )
            throw new DBusException(c + " is not a basic type");

        if ( c instanceof TypeVariable )
            out[ level ].append((char) Message.ArgumentType.VARIANT);
        else if ( c instanceof GenericArrayType ) {
            out[ level ].append((char) Message.ArgumentType.ARRAY);
            String[] s = recursiveGetDBusType( ( (GenericArrayType) c ).getGenericComponentType(), false, level + 1);
            if ( s.length != 1 )
                throw new DBusException("Multi-valued array types not permitted");
            out[ level ].append(s[ 0 ]);
        }
        else if ( ( c instanceof Class && DBusSerializable.class.isAssignableFrom((Class<? extends Object>) c) )
                || ( c instanceof ParameterizedType && DBusSerializable.class.isAssignableFrom((Class<? extends Object>) ( (ParameterizedType) c )
                        .getRawType()) ) ) {
            // it's a custom serializable type
            Type[] newtypes = null;
            if ( c instanceof Class ) {
                for ( Method m : ( (Class<? extends Object>) c ).getDeclaredMethods() )
                    if ( m.getName().equals("deserialize") )
                        newtypes = m.getGenericParameterTypes();
            }
            else
                for ( Method m : ( (Class<? extends Object>) ( (ParameterizedType) c ).getRawType() ).getDeclaredMethods() )
                    if ( m.getName().equals("deserialize") )
                        newtypes = m.getGenericParameterTypes();

            if ( null == newtypes )
                throw new DBusException("Serializable classes must implement a deserialize method");

            String[] sigs = new String[newtypes.length];
            for ( int j = 0; j < sigs.length; j++ ) {
                String[] ss = recursiveGetDBusType(newtypes[ j ], false, level + 1);
                if ( 1 != ss.length )
                    throw new DBusException("Serializable classes must serialize to native DBus types");
                sigs[ j ] = ss[ 0 ];
            }
            return sigs;
        }
        else if ( c instanceof ParameterizedType ) {
            ParameterizedType p = (ParameterizedType) c;
            if ( p.getRawType().equals(Map.class) ) {
                out[ level ].append("a{");
                Type[] t = p.getActualTypeArguments();
                try {
                    String[] s = recursiveGetDBusType(t[ 0 ], true, level + 1);
                    if ( s.length != 1 )
                        throw new DBusException("Multi-valued array types not permitted");
                    out[ level ].append(s[ 0 ]);
                    s = recursiveGetDBusType(t[ 1 ], false, level + 1);
                    if ( s.length != 1 )
                        throw new DBusException("Multi-valued array types not permitted");
                    out[ level ].append(s[ 0 ]);
                }
                catch ( ArrayIndexOutOfBoundsException AIOOBe ) {
                    log.warn(AIOOBe);
                    throw new DBusException("Map must have 2 parameters");
                }
                out[ level ].append('}');
            }
            else if ( List.class.isAssignableFrom((Class<? extends Object>) p.getRawType()) ) {
                for ( Type t : p.getActualTypeArguments() ) {
                    if ( Type.class.equals(t) )
                        out[ level ].append((char) Message.ArgumentType.SIGNATURE);
                    else {
                        String[] s = recursiveGetDBusType(t, false, level + 1);
                        if ( s.length != 1 )
                            throw new DBusException("Multi-valued array types not permitted");
                        out[ level ].append((char) Message.ArgumentType.ARRAY);
                        out[ level ].append(s[ 0 ]);
                    }
                }
            }
            else if ( p.getRawType().equals(Variant.class) ) {
                out[ level ].append((char) Message.ArgumentType.VARIANT);
            }
            else if ( DBusInterface.class.isAssignableFrom((Class<? extends Object>) p.getRawType()) ) {
                out[ level ].append((char) Message.ArgumentType.OBJECT_PATH);
            }
            else if ( Tuple.class.isAssignableFrom((Class<? extends Object>) p.getRawType()) ) {
                Type[] ts = p.getActualTypeArguments();
                Vector<String> vs = new Vector<>();
                for ( Type t : ts )
                    for ( String s : recursiveGetDBusType(t, false, level + 1) )
                        vs.add(s);
                return vs.toArray(new String[0]);
            }
            else
                throw new DBusException("Exporting non-exportable parameterized type " + c);
        }

        else if ( c.equals(Byte.class) )
            out[ level ].append((char) Message.ArgumentType.BYTE);
        else if ( c.equals(Byte.TYPE) )
            out[ level ].append((char) Message.ArgumentType.BYTE);
        else if ( c.equals(Boolean.class) )
            out[ level ].append((char) Message.ArgumentType.BOOLEAN);
        else if ( c.equals(Boolean.TYPE) )
            out[ level ].append((char) Message.ArgumentType.BOOLEAN);
        else if ( c.equals(Short.class) )
            out[ level ].append((char) Message.ArgumentType.INT16);
        else if ( c.equals(Short.TYPE) )
            out[ level ].append((char) Message.ArgumentType.INT16);
        else if ( c.equals(UInt16.class) )
            out[ level ].append((char) Message.ArgumentType.UINT16);
        else if ( c.equals(Integer.class) )
            out[ level ].append((char) Message.ArgumentType.INT32);
        else if ( c.equals(Integer.TYPE) )
            out[ level ].append((char) Message.ArgumentType.INT32);
        else if ( c.equals(UInt32.class) )
            out[ level ].append((char) Message.ArgumentType.UINT32);
        else if ( c.equals(Long.class) )
            out[ level ].append((char) Message.ArgumentType.INT64);
        else if ( c.equals(Long.TYPE) )
            out[ level ].append((char) Message.ArgumentType.INT64);
        else if ( c.equals(UInt64.class) )
            out[ level ].append((char) Message.ArgumentType.UINT64);
        else if ( c.equals(Double.class) )
            out[ level ].append((char) Message.ArgumentType.DOUBLE);
        else if ( c.equals(Double.TYPE) )
            out[ level ].append((char) Message.ArgumentType.DOUBLE);
        else if ( c.equals(Float.class) && AbstractConnection.FLOAT_SUPPORT )
            out[ level ].append((char) Message.ArgumentType.FLOAT);
        else if ( c.equals(Float.class) )
            out[ level ].append((char) Message.ArgumentType.DOUBLE);
        else if ( c.equals(Float.TYPE) && AbstractConnection.FLOAT_SUPPORT )
            out[ level ].append((char) Message.ArgumentType.FLOAT);
        else if ( c.equals(Float.TYPE) )
            out[ level ].append((char) Message.ArgumentType.DOUBLE);
        else if ( c.equals(String.class) )
            out[ level ].append((char) Message.ArgumentType.STRING);
        else if ( c.equals(Variant.class) )
            out[ level ].append((char) Message.ArgumentType.VARIANT);
        else if ( c instanceof Class && DBusInterface.class.isAssignableFrom((Class<? extends Object>) c) )
            out[ level ].append((char) Message.ArgumentType.OBJECT_PATH);
        else if ( c instanceof Class && Path.class.equals(c) )
            out[ level ].append((char) Message.ArgumentType.OBJECT_PATH);
        else if ( c instanceof Class && ObjectPath.class.equals(c) )
            out[ level ].append((char) Message.ArgumentType.OBJECT_PATH);
        else if ( c.equals(UnixFD.class) )
            out[ level ].append((char) Message.ArgumentType.UNIX_FD);
        else if ( c instanceof Class && ( (Class<? extends Object>) c ).isArray() ) {
            if ( Type.class.equals( ( (Class<? extends Object>) c ).getComponentType()) )
                out[ level ].append((char) Message.ArgumentType.SIGNATURE);
            else {
                out[ level ].append((char) Message.ArgumentType.ARRAY);
                String[] s = recursiveGetDBusType( ( (Class<? extends Object>) c ).getComponentType(), false, level + 1);
                if ( s.length != 1 )
                    throw new DBusException("Multi-valued array types not permitted");
                out[ level ].append(s[ 0 ]);
            }
        }
        else if ( c instanceof Class && Struct.class.isAssignableFrom((Class<? extends Object>) c) ) {
            out[ level ].append((char) Message.ArgumentType.STRUCT1);
            Type[] ts = Container.getTypeCache(c);
            if ( null == ts ) {
                Field[] fs = ( (Class<? extends Object>) c ).getDeclaredFields();
                ts = new Type[fs.length];
                for ( Field f : fs ) {
                    Position p = f.getAnnotation(Position.class);
                    if ( null == p )
                        continue;
                    ts[ p.value() ] = f.getGenericType();
                }
                Container.putTypeCache(c, ts);
            }

            for ( Type t : ts )
                if ( t != null )
                    for ( String s : recursiveGetDBusType(t, false, level + 1) )
                        out[ level ].append(s);
            out[ level ].append(')');
        }
        else {
            throw new DBusException("Exporting non-exportable type " + c);
        }

        if ( log.isTraceEnabled() ) {
            log.trace("Converted Java type: " + c + " to D-Bus Type: " + out[ level ]);
        }

        return new String[] {
            out[ level ].toString()
        };
    }


    /**
     * Converts a dbus type string into Java Type objects,
     * 
     * @param dbus
     *            The DBus type or types.
     * @param rv
     *            Vector to return the types in.
     * @param limit
     *            Maximum number of types to parse (-1 == nolimit).
     * @return number of characters parsed from the type string.
     */
    public static int getJavaType ( String dbus, List<Type> rv, int limit ) throws DBusException {
        if ( null == dbus || "".equals(dbus) || 0 == limit )
            return 0;

        try {
            int i = 0;
            for ( ; i < dbus.length() && ( -1 == limit || limit > rv.size() ); i++ )
                switch ( dbus.charAt(i) ) {
                case Message.ArgumentType.STRUCT1:
                    int j = i + 1;
                    for ( int c = 1; c > 0; j++ ) {
                        if ( ')' == dbus.charAt(j) )
                            c--;
                        else if ( Message.ArgumentType.STRUCT1 == dbus.charAt(j) )
                            c++;
                    }

                    Vector<Type> contained = new Vector<>();
                    int c = getJavaType(dbus.substring(i + 1, j - 1), contained, -1);
                    rv.add(new DBusStructType(contained.toArray(new Type[0])));
                    i = j;
                    break;
                case Message.ArgumentType.ARRAY:
                    if ( Message.ArgumentType.DICT_ENTRY1 == dbus.charAt(i + 1) ) {
                        contained = new Vector<>();
                        c = getJavaType(dbus.substring(i + 2), contained, 2);
                        rv.add(new DBusMapType(contained.get(0), contained.get(1)));
                        i += ( c + 2 );
                    }
                    else {
                        contained = new Vector<>();
                        c = getJavaType(dbus.substring(i + 1), contained, 1);
                        rv.add(new DBusListType(contained.get(0)));
                        i += c;
                    }
                    break;
                case Message.ArgumentType.VARIANT:
                    rv.add(Variant.class);
                    break;
                case Message.ArgumentType.BOOLEAN:
                    rv.add(Boolean.class);
                    break;
                case Message.ArgumentType.INT16:
                    rv.add(Short.class);
                    break;
                case Message.ArgumentType.BYTE:
                    rv.add(Byte.class);
                    break;
                case Message.ArgumentType.OBJECT_PATH:
                    rv.add(DBusInterface.class);
                    break;
                case Message.ArgumentType.UINT16:
                    rv.add(UInt16.class);
                    break;
                case Message.ArgumentType.INT32:
                    rv.add(Integer.class);
                    break;
                case Message.ArgumentType.UINT32:
                    rv.add(UInt32.class);
                    break;
                case Message.ArgumentType.INT64:
                    rv.add(Long.class);
                    break;
                case Message.ArgumentType.UINT64:
                    rv.add(UInt64.class);
                    break;
                case Message.ArgumentType.DOUBLE:
                    rv.add(Double.class);
                    break;
                case Message.ArgumentType.FLOAT:
                    rv.add(Float.class);
                    break;
                case Message.ArgumentType.STRING:
                    rv.add(String.class);
                    break;
                case Message.ArgumentType.SIGNATURE:
                    rv.add(Type[].class);
                    break;
                case Message.ArgumentType.UNIX_FD:
                    rv.add(UnixFD.class);
                    break;
                case Message.ArgumentType.DICT_ENTRY1:
                    rv.add(Map.Entry.class);
                    contained = new Vector<>();
                    c = getJavaType(dbus.substring(i + 1), contained, 2);
                    i += c + 1;
                    break;
                default:
                    throw new DBusException(String.format("Failed to parse DBus type signature: %s (%s).", dbus, dbus.charAt(i)));
                }
            return i;
        }
        catch ( IndexOutOfBoundsException IOOBe ) {
            log.warn("", IOOBe);
            throw new DBusException("Failed to parse DBus type signature: " + dbus);
        }
        catch ( DBusException e ) {
            log.warn("Failed to determine object type:", e);
            throw e;
        }
    }


    /**
     * Recursively converts types for serialization onto DBus.
     * 
     * @param parameters
     *            The parameters to convert.
     * @param types
     *            The (possibly generic) types of the parameters.
     * @return The converted parameters.
     * @throws DBusException
     *             Thrown if there is an error in converting the objects.
     */
    public static Object[] convertParameters ( Object[] parameters, Type[] types, AbstractConnection conn ) throws DBusException {
        if ( null == parameters )
            return null;

        Type[] tmpTypes = Arrays.copyOf(types, types.length);
        Object[] tmpParams = Arrays.copyOf(parameters, parameters.length);

        for ( int i = 0; i < tmpParams.length; i++ ) {
            if ( log.isTraceEnabled() ) {
                log.trace("Converting " + i + " from " + tmpParams[ i ] + " to " + tmpTypes[ i ]);
            }
            if ( null == tmpParams[ i ] )
                continue;

            if ( tmpParams[ i ] instanceof DBusSerializable ) {
                for ( Method m : tmpParams[ i ].getClass().getDeclaredMethods() )
                    if ( m.getName().equals("deserialize") ) {
                        Type[] newtypes = m.getParameterTypes();
                        Type[] expand = new Type[tmpTypes.length + newtypes.length - 1];
                        System.arraycopy(tmpTypes, 0, expand, 0, i);
                        System.arraycopy(newtypes, 0, expand, i, newtypes.length);
                        System.arraycopy(tmpTypes, i + 1, expand, i + newtypes.length, tmpTypes.length - i - 1);
                        tmpTypes = expand;
                        Object[] newparams = ( (DBusSerializable) tmpParams[ i ] ).serialize();
                        Object[] exparams = new Object[tmpParams.length + newparams.length - 1];
                        System.arraycopy(tmpParams, 0, exparams, 0, i);
                        System.arraycopy(newparams, 0, exparams, i, newparams.length);
                        System.arraycopy(tmpParams, i + 1, exparams, i + newparams.length, tmpParams.length - i - 1);
                        tmpParams = exparams;
                    }
                i--;
            }
            else if ( tmpParams[ i ] instanceof Tuple ) {
                Type[] newtypes = ( (ParameterizedType) tmpTypes[ i ] ).getActualTypeArguments();
                Type[] expand = new Type[tmpTypes.length + newtypes.length - 1];
                System.arraycopy(tmpTypes, 0, expand, 0, i);
                System.arraycopy(newtypes, 0, expand, i, newtypes.length);
                System.arraycopy(tmpTypes, i + 1, expand, i + newtypes.length, tmpTypes.length - i - 1);
                tmpTypes = expand;
                Object[] newparams = ( (Tuple) tmpParams[ i ] ).getParameters();
                Object[] exparams = new Object[tmpParams.length + newparams.length - 1];
                System.arraycopy(tmpParams, 0, exparams, 0, i);
                System.arraycopy(newparams, 0, exparams, i, newparams.length);
                System.arraycopy(tmpParams, i + 1, exparams, i + newparams.length, tmpParams.length - i - 1);
                tmpParams = exparams;
                if ( log.isTraceEnabled() ) {
                    log.trace("New params: " + Arrays.deepToString(tmpParams) + " new types: " + Arrays.deepToString(tmpTypes));
                }
                i--;
            }
            else if ( tmpTypes[ i ] instanceof TypeVariable && ! ( tmpParams[ i ] instanceof Variant ) )
                // its an unwrapped variant, wrap it
                tmpParams[ i ] = new Variant<>(tmpParams[ i ]);
            else if ( tmpParams[ i ] instanceof DBusInterface )
                tmpParams[ i ] = conn.getExportedObject((DBusInterface) tmpParams[ i ]);
        }
        return tmpParams;
    }


    @SuppressWarnings ( "unchecked" )
    static Object deSerializeParameter ( Object parameter, Type type, AbstractConnection conn ) throws Exception {
        if ( log.isTraceEnabled() ) {
            log.trace("Deserializing from " + parameter.getClass() + " to " + type.getClass());
        }
        if ( null == parameter )
            return null;

        Object tmpParam = parameter;
        Type tmpType = type;

        // its a wrapped variant, unwrap it
        if ( tmpType instanceof TypeVariable && tmpParam instanceof Variant ) {
            tmpParam = ( (Variant<?>) tmpParam ).getValue();
        }

        // Turn a signature into a Type[]
        if ( tmpType instanceof Class && ( (Class<?>) tmpType ).isArray() && ( (Class<?>) tmpType ).getComponentType().equals(Type.class)
                && tmpParam instanceof String ) {
            Vector<Type> rv = new Vector<>();
            getJavaType((String) tmpParam, rv, -1);
            tmpParam = rv.toArray(new Type[0]);
        }

        // its an object path, get/create the proxy
        if ( tmpParam instanceof ObjectPath ) {
            if ( tmpType instanceof Class && DBusInterface.class.isAssignableFrom((Class<?>) tmpType) )
                tmpParam = conn.getExportedObject( ( (ObjectPath) tmpParam ).source, ( (ObjectPath) tmpParam ).path);
            else
                tmpParam = new Path( ( (ObjectPath) tmpParam ).path);
        }

        // it should be a struct. create it
        if ( tmpParam instanceof Object[] && tmpType instanceof Class && Struct.class.isAssignableFrom((Class<?>) tmpType) ) {
            if ( log.isTraceEnabled() ) {
                log.trace("Creating Struct " + tmpType + " from " + tmpParam);
            }
            Type[] ts = Container.getTypeCache(tmpType);
            if ( null == ts ) {
                Field[] fs = ( (Class<?>) tmpType ).getDeclaredFields();
                ts = new Type[fs.length];
                for ( Field f : fs ) {
                    Position p = f.getAnnotation(Position.class);
                    if ( null == p )
                        continue;
                    ts[ p.value() ] = f.getGenericType();
                }
                Container.putTypeCache(tmpType, ts);
            }

            // recurse over struct contents
            tmpParam = deSerializeParameters((Object[]) tmpParam, ts, conn);
            for ( Constructor<?> con : ( (Class<?>) tmpType ).getDeclaredConstructors() ) {
                try {
                    tmpParam = con.newInstance((Object[]) tmpParam);
                    break;
                }
                catch ( IllegalArgumentException IAe ) {}
            }
        }

        // recurse over arrays
        if ( tmpParam instanceof Object[] ) {
            Type[] ts = new Type[ ( (Object[]) tmpParam ).length];
            Arrays.fill(ts, tmpParam.getClass().getComponentType());
            tmpParam = deSerializeParameters((Object[]) tmpParam, ts, conn);
        }
        if ( tmpParam instanceof List ) {
            Type type2;
            if ( tmpType instanceof ParameterizedType )
                type2 = ( (ParameterizedType) tmpType ).getActualTypeArguments()[ 0 ];
            else if ( tmpType instanceof GenericArrayType )
                type2 = ( (GenericArrayType) tmpType ).getGenericComponentType();
            else if ( tmpType instanceof Class && ( (Class<?>) tmpType ).isArray() )
                type2 = ( (Class<?>) tmpType ).getComponentType();
            else
                type2 = null;
            if ( null != type2 )
                tmpParam = deSerializeParameters((List<Object>) tmpParam, type2, conn);
        }

        // correct floats if appropriate
        if ( tmpType.equals(Float.class) || tmpType.equals(Float.TYPE) )
            if ( ! ( tmpParam instanceof Float ) )
                tmpParam = ( (Number) tmpParam ).floatValue();

        // make sure arrays are in the correct format
        if ( tmpParam instanceof Object[] || tmpParam instanceof List || tmpParam.getClass().isArray() ) {
            if ( tmpType instanceof ParameterizedType )
                tmpParam = ArrayFrob.convert(tmpParam, (Class<? extends Object>) ( (ParameterizedType) tmpType ).getRawType());
            else if ( tmpType instanceof GenericArrayType ) {
                Type ct = ( (GenericArrayType) tmpType ).getGenericComponentType();
                Class<?> cc = null;
                if ( ct instanceof Class )
                    cc = (Class<?>) ct;
                if ( ct instanceof ParameterizedType )
                    cc = (Class<?>) ( (ParameterizedType) ct ).getRawType();
                Object o = Array.newInstance(cc, 0);
                tmpParam = ArrayFrob.convert(tmpParam, o.getClass());
            }
            else if ( tmpType instanceof Class && ( (Class<?>) tmpType ).isArray() ) {
                Class<?> cc = ( (Class<?>) tmpType ).getComponentType();
                if ( ( cc.equals(Float.class) || cc.equals(Float.TYPE) ) && ( tmpParam instanceof double[] ) ) {
                    double[] tmp1 = (double[]) tmpParam;
                    float[] tmp2 = new float[tmp1.length];
                    for ( int i = 0; i < tmp1.length; i++ )
                        tmp2[ i ] = (float) tmp1[ i ];
                    tmpParam = tmp2;
                }
                Object o = Array.newInstance(cc, 0);
                tmpParam = ArrayFrob.convert(tmpParam, o.getClass());
            }
        }
        if ( tmpParam instanceof DBusMap ) {
            if ( log.isTraceEnabled() ) {
                log.trace("Deserializing a Map");
            }
            DBusMap<?, ?> dmap = (DBusMap<?, ?>) tmpParam;
            Type[] maptypes = ( (ParameterizedType) tmpType ).getActualTypeArguments();
            for ( int i = 0; i < dmap.entries.length; i++ ) {
                dmap.entries[ i ][ 0 ] = deSerializeParameter(dmap.entries[ i ][ 0 ], maptypes[ 0 ], conn);
                dmap.entries[ i ][ 1 ] = deSerializeParameter(dmap.entries[ i ][ 1 ], maptypes[ 1 ], conn);
            }
        }
        return tmpParam;
    }


    static List<Object> deSerializeParameters ( List<Object> parameters, Type type, AbstractConnection conn ) throws Exception {
        if ( log.isTraceEnabled() ) {
            log.trace("Deserializing from " + parameters + " to " + type);
        }
        if ( null == parameters )
            return null;
        for ( int i = 0; i < parameters.size(); i++ ) {
            if ( null == parameters.get(i) )
                continue;

            parameters.set(i, deSerializeParameter(parameters.get(i), type, conn));
        }
        return parameters;
    }


    @SuppressWarnings ( "unchecked" )
    static Object[] deSerializeParameters ( Object[] parameters, Type[] types, AbstractConnection conn ) throws Exception {
        if ( log.isTraceEnabled() ) {
            log.trace("Deserializing from " + Arrays.deepToString(parameters) + " to " + Arrays.deepToString(types));
        }
        if ( null == parameters )
            return null;

        Type[] tmpTypes = Arrays.copyOf(types, types.length);
        Object[] tmpParams = Arrays.copyOf(parameters, parameters.length);

        if ( tmpTypes.length == 1 && tmpTypes[ 0 ] instanceof ParameterizedType
                && Tuple.class.isAssignableFrom((Class<?>) ( (ParameterizedType) tmpTypes[ 0 ] ).getRawType()) ) {
            tmpTypes = ( (ParameterizedType) tmpTypes[ 0 ] ).getActualTypeArguments();
        }

        for ( int i = 0; i < tmpParams.length; i++ ) {
            // CHECK IF ARRAYS HAVE THE SAME LENGTH <-- has to happen after expanding parameters
            if ( i >= tmpTypes.length ) {
                for ( int j = 0; j < tmpParams.length; j++ ) {
                    log.warn(String.format("Error, Parameters difference (%1d, '%2s')", j, tmpParams[ j ].toString()));
                }
                throw new DBusException("Error deserializing message: number of parameters didn't match receiving signature");
            }
            if ( null == tmpParams[ i ] )
                continue;

            if ( ( tmpTypes[ i ] instanceof Class && DBusSerializable.class.isAssignableFrom((Class<? extends Object>) tmpTypes[ i ]) )
                    || ( tmpTypes[ i ] instanceof ParameterizedType && DBusSerializable.class
                            .isAssignableFrom((Class<? extends Object>) ( (ParameterizedType) tmpTypes[ i ] ).getRawType()) ) ) {
                Class<? extends DBusSerializable> dsc;
                if ( tmpTypes[ i ] instanceof Class )
                    dsc = (Class<? extends DBusSerializable>) tmpTypes[ i ];
                else
                    dsc = (Class<? extends DBusSerializable>) ( (ParameterizedType) tmpTypes[ i ] ).getRawType();
                for ( Method m : dsc.getDeclaredMethods() )
                    if ( m.getName().equals("deserialize") ) {
                        Type[] newtypes = m.getGenericParameterTypes();
                        try {
                            Object[] sub = new Object[newtypes.length];
                            System.arraycopy(tmpParams, i, sub, 0, newtypes.length);
                            sub = deSerializeParameters(sub, newtypes, conn);
                            DBusSerializable sz = dsc.newInstance();
                            m.invoke(sz, sub);
                            Object[] compress = new Object[tmpParams.length - newtypes.length + 1];
                            System.arraycopy(tmpParams, 0, compress, 0, i);
                            compress[ i ] = sz;
                            System.arraycopy(tmpParams, i + newtypes.length, compress, i + 1, tmpParams.length - i - newtypes.length);
                            tmpParams = compress;
                        }
                        catch ( ArrayIndexOutOfBoundsException AIOOBe ) {
                            log.warn(AIOOBe);
                            throw new DBusException(String.format(
                                "Not enough elements to create custom object from serialized data ({0} < {1}).",
                                tmpParams.length - i,
                                newtypes.length));
                        }
                    }
            }
            else
                tmpParams[ i ] = deSerializeParameter(tmpParams[ i ], tmpTypes[ i ], conn);
        }
        return tmpParams;
    }
}