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

// This is a dummy file to compile on platforms where namespace sandboxing
// doesn't work (ie. other than Linux). We need this for main/tools/BUILD file
// - we can't restrict visibility of namespace-sandbox based on platform;
// instead bazel build main/tools:namespace-sandbox is a no-op on non supported
// platforms (if we didn't have this file, it would fail with a non-informative
// message)

int main() {
  return 0;
}
