/*
 * Copyright  (c) 2017 Lyloou
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lyloou.demo.aidl;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;

import com.lyloou.demo.Manifest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class BookManagerService extends Service {
    AtomicBoolean mIsDestroy = new AtomicBoolean(false);

    CopyOnWriteArrayList<Book> mBooks = new CopyOnWriteArrayList<>();

    RemoteCallbackList<IOnNewBookArrivedListener> mListeners = new RemoteCallbackList<>();
    Binder mBinder = new IBookManager.Stub() {

        @Override
        public List<Book> getBookList() throws RemoteException {
            SystemClock.sleep(300);
            return mBooks;
        }

        @Override
        public void addBook(Book book) throws RemoteException {
            mBooks.add(book);
        }

        @Override
        public void registerListener(IOnNewBookArrivedListener listener) throws RemoteException {
            mListeners.register(listener);
            System.out.println("监听个数增加到了" + mListeners.getRegisteredCallbackCount());
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (isPermissionDenied()) return false;
            String[] packagesForUid = getPackageManager().getPackagesForUid(getCallingUid());
            if(packagesForUid != null && packagesForUid.length>0){
                String packagesName = packagesForUid[0];
                if (!packagesName.startsWith("com.lyloou")) {
                    return false;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        @Override
        public void unregisterListener(IOnNewBookArrivedListener listener) throws RemoteException {
            mListeners.unregister(listener);
            System.out.println("剩余监听个数" + mListeners.getRegisteredCallbackCount());
        }
    };

    public BookManagerService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBooks.add(new Book(1, "Android"));
        mBooks.add(new Book(2, "iOs"));

        new Thread(new CreateBookWorker()).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mIsDestroy.set(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (isPermissionDenied()) return null;

        return mBinder;
    }

    private boolean isPermissionDenied() {
        int check = checkCallingOrSelfPermission(Manifest.permission.ACCESS_BOOK_SERVICE);
        return PackageManager.PERMISSION_DENIED == check;
    }

    private void onNewBookArrived(Book book) throws RemoteException {
        mBooks.add(book);

        int N = mListeners.beginBroadcast();
        for (int i = 0; i < N; i++) {
            IOnNewBookArrivedListener broadcastItem = mListeners.getBroadcastItem(i);
            broadcastItem.onNewBookArrived(book);
        }
        mListeners.finishBroadcast();
    }

    private class CreateBookWorker implements Runnable {
        @Override
        public void run() {
            while (!mIsDestroy.get()) {
                SystemClock.sleep(3600);
                int bookId = mBooks.size() + 1;
                try {
                    onNewBookArrived(new Book(bookId, "《新书" + bookId + "》"));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
