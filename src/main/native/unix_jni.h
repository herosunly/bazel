// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// INTERNAL header file for use by C++ code in this package.

#ifndef JAVA_COM_GOOGLE_DEVTOOLS_BUILD_LIB_UNIX_UNIX_JNI_H__
#define JAVA_COM_GOOGLE_DEVTOOLS_BUILD_LIB_UNIX_UNIX_JNI_H__

#include <jni.h>

#include <string>

#define CHECK(condition) \
    do { \
      if (!(condition)) { \
        fprintf(stderr, "%s:%d: check failed: %s\n", \
                __FILE__, __LINE__, #condition); \
        abort(); \
      } \
    } while (0)

// Posts a JNI exception to the current thread with the specified
// message; the exception's class is determined by the specified UNIX
// error number.  See package-info.html for details.
extern void PostException(JNIEnv *env, int error_number,
                          const std::string &message);

// Like PostException, but the exception message includes both the
// specified filename and the standard UNIX error message for the
// error number.
// (Consistent with errors generated by java.io package.)
extern void PostFileException(JNIEnv *env, int error_number,
                              const char *filename);

// Returns the standard error message for a given UNIX error number.
extern std::string ErrorMessage(int error_number);

// Runs fstatat(2), if available, or sets errno to ENOSYS if not.
int portable_fstatat(int dirfd, char *name, struct stat *statbuf, int flags);

// Encoding for different timestamps in a struct stat{}.
enum StatTimes {
  STAT_ATIME,  // access
  STAT_MTIME,  // modification
  STAT_CTIME,  // status change
};

// Returns seconds from a stat buffer.
int StatSeconds(const struct stat &statbuf, StatTimes t);

// Returns nanoseconds from a stat buffer.
int StatNanoSeconds(const struct stat &statbuf, StatTimes t);

// Runs getxattr(2), if available. If not, sets errno to ENOSYS.
ssize_t portable_getxattr(const char *path, const char *name, void *value,
                          size_t size);

// Run lgetxattr(2), if available. If not, sets errno to ENOSYS.
ssize_t portable_lgetxattr(const char *path, const char *name, void *value,
                           size_t size);

#endif  // JAVA_COM_GOOGLE_DEVTOOLS_BUILD_LIB_UNIX_UNIX_JNI_H__
