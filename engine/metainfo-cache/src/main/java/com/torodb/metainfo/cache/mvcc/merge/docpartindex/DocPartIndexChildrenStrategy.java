/*
 * ToroDB
 * Copyright © 2014 8Kdata Technology (www.8kdata.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.torodb.metainfo.cache.mvcc.merge.docpartindex;

import com.torodb.core.transaction.metainf.ImmutableMetaDocPart;
import com.torodb.core.transaction.metainf.ImmutableMetaIdentifiedDocPartIndex;
import com.torodb.core.transaction.metainf.ImmutableMetaIdentifiedDocPartIndex.Builder;
import com.torodb.core.transaction.metainf.MetaIdentifiedDocPartIndex;
import com.torodb.metainfo.cache.mvcc.merge.ChildrenMergePartialStrategy;
import com.torodb.metainfo.cache.mvcc.merge.index.field.column.IndexColumnCtx;
import com.torodb.metainfo.cache.mvcc.merge.index.field.column.IndexColumnMergeStrategy;
import com.torodb.metainfo.cache.mvcc.merge.result.ExecutionResult;
import com.torodb.metainfo.cache.mvcc.merge.result.ParentDescriptionFun;

import java.util.stream.Stream;

public class DocPartIndexChildrenStrategy extends ChildrenMergePartialStrategy<
    ImmutableMetaDocPart, MetaIdentifiedDocPartIndex, ImmutableMetaDocPart.Builder, DocPartIndexCtx,
    Builder, ImmutableMetaIdentifiedDocPartIndex>
    implements DocPartIndexPartialStrategy {

  private final IndexColumnMergeStrategy columnStrategy = new IndexColumnMergeStrategy();

  @Override
  public boolean appliesTo(DocPartIndexCtx context) {
    return getCommitedById(context) == null;
  }

  @Override
  protected ImmutableMetaIdentifiedDocPartIndex.Builder createSelfBuilder(DocPartIndexCtx context) {
    ImmutableMetaIdentifiedDocPartIndex oldById = getCommitedById(context);
    assert oldById != null;

    return new ImmutableMetaIdentifiedDocPartIndex.Builder(oldById);
  }

  @Override
  protected Stream<ExecutionResult<ImmutableMetaIdentifiedDocPartIndex>> streamChildResults(
      DocPartIndexCtx context, Builder selfBuilder) {
    ImmutableMetaIdentifiedDocPartIndex oldById = getCommitedById(context);
    return context.getChanged().streamColumns()
        .map(column -> new IndexColumnCtx(
            oldById,
            column,
            context.getChanged()
        ))
        .map(ctx -> columnStrategy.execute(ctx, selfBuilder));
  }

  @Override
  protected void changeParent(ImmutableMetaDocPart.Builder parentBuilder,
      ImmutableMetaIdentifiedDocPartIndex.Builder selfBuilder) {
    parentBuilder.put(selfBuilder);
  }

  @Override
  protected String describeChanged(
      ParentDescriptionFun<ImmutableMetaDocPart> parentDescFun,
      ImmutableMetaDocPart parent, ImmutableMetaIdentifiedDocPartIndex immutableSelf) {
    return parentDescFun.apply(parent) + '.' + immutableSelf.getIdentifier();
  }

}