/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.connectivity.mdns.internal;

import android.annotation.NonNull;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.net.module.util.DnsUtils;
import com.android.net.module.util.SharedLog;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * A repository storing whether a given UID can discover a given service.
 *
 * <p>This class is not thread-safe, and all methods are expected to be called on the NsdService
 * handler thread.
 */
public class ServiceAccessRepository {
    private final ArrayMap<PackageEntry, ArraySet<Service>> mAllowedServices = new ArrayMap<>();
    private final SharedLog mSharedLog;

    /**
     * Build a new {@link ServiceAccessRepository}.
     */
    public ServiceAccessRepository(@NonNull SharedLog sharedLog) {
        mSharedLog = sharedLog;
    }

    /**
     * Add a service that the given UID is allowed to discover.
     */
    public void addAllowedService(int uid, @NonNull String packageName, @NonNull String serviceName,
            @NonNull String serviceType) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(serviceName);
        Objects.requireNonNull(serviceType);
        final PackageEntry pkg = new PackageEntry(uid, packageName);
        final Service service = new Service(serviceName, serviceType);
        ArraySet<Service> services = mAllowedServices.get(pkg);
        if (services == null) {
            services = new ArraySet<>();
            mAllowedServices.put(pkg, services);
        }
        services.add(service);
        mSharedLog.log("Added " + serviceName + "." + serviceType + " for UID " + uid);

        // TODO: asynchronously persist the service to disk
    }

    /**
     * Query whether a given UID is allowed to discover a given service.
     */
    public boolean isServiceAllowed(int uid, @NonNull String packageName,
            @NonNull String serviceName, @NonNull String serviceType) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(serviceName);
        Objects.requireNonNull(serviceType);
        final ArraySet<Service> services = mAllowedServices.get(new PackageEntry(uid, packageName));
        if (services == null) {
            return false;
        }
        return services.contains(new Service(serviceName, serviceType));
    }

    /**
     * Load the list of allowed services from disk for a given package.
     */
    public void loadPackage(int uid, @NonNull String packageName) {
        // TODO: load allowed services from disk
    }

    /**
     * Unload the list of allowed services for a given UID.
     *
     * <p>Allowed services can be reloaded from disk using {@link #loadPackage(int, String)}.
     */
    public void unloadPackage(int uid, @NonNull String packageName) {
        mAllowedServices.remove(new PackageEntry(uid, packageName));
    }

    /**
     * Dump the contents of the repository for logging purposes.
     */
    public void dump(PrintWriter pw) {
        for (int i = 0; i < mAllowedServices.size(); i++) {
            final PackageEntry pkg = mAllowedServices.keyAt(i);
            final ArraySet<Service> services = mAllowedServices.valueAt(i);
            pw.println(pkg + ":");
            for (int j = 0; j < services.size(); j++) {
                final Service service = services.valueAt(j);
                pw.println("  " + service);
            }
        }
    }

    public static class PackageEntry {
        public final int uid;
        @NonNull
        public final String packageName;

        PackageEntry(int uid, @NonNull String packageName) {
            this.uid = uid;
            this.packageName = packageName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PackageEntry)) return false;
            PackageEntry pkg = (PackageEntry) o;
            return Objects.equals(packageName, pkg.packageName) && uid == pkg.uid;
        }

        @Override
        public int hashCode() {
            // Similar to Objects.hashCode but avoids boxing into an array
            return 31 * uid + packageName.hashCode();
        }

        @NonNull
        @Override
        public String toString() {
            return packageName + " (" + uid + ")";
        }
    }

    public static class Service {
        @NonNull
        final String mName;
        @NonNull
        final String mType;

        Service(@NonNull String name, @NonNull String type) {
            this.mName = DnsUtils.toDnsUpperCase(name);
            this.mType = DnsUtils.toDnsUpperCase(type);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Service)) return false;
            Service service = (Service) o;
            return Objects.equals(mName, service.mName) && Objects.equals(mType, service.mType);
        }

        @Override
        public int hashCode() {
            // Similar to Objects.hashCode but avoids boxing into an array
            return 31 * mName.hashCode() + mType.hashCode();
        }

        @NonNull
        @Override
        public String toString() {
            return mName + "." + mType;
        }
    }
}
