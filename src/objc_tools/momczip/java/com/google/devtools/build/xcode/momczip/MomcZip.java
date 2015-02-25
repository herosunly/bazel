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

package com.google.devtools.build.xcode.momczip;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.xcode.zippingoutput.Arguments;
import com.google.devtools.build.xcode.zippingoutput.Wrapper;
import com.google.devtools.build.xcode.zippingoutput.Wrappers;

import java.io.IOException;

/**
 * A tool which wraps momc, by running momc and zipping its output. See the JavaDoc for
 * {@link Wrapper} for more information.
 */
public class MomcZip implements Wrapper {
  @Override
  public String name() {
    return "MomcZip";
  }

  @Override
  public String subtoolName() {
    return "momc";
  }

  @Override
  public Iterable<String> subCommand(Arguments args, String outputDirectory) {
    return new ImmutableList.Builder<String>()
        .add(args.subtoolCmd())
        .addAll(args.subtoolExtraArgs())
        .add(outputDirectory)
        .build();
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    Wrappers.execute(args, new MomcZip());
  }

  @Override
  public boolean outputDirectoryMustExist() {
    return false;
  }
}
