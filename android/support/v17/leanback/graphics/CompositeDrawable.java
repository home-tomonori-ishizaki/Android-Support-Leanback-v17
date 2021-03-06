/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.support.v17.leanback.graphics;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.Property;

import java.util.ArrayList;

/**
 * Generic drawable class that can be composed of multiple children. Whenever the bounds changes
 * for this class, it updates those of it's children.
 * @hide
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class CompositeDrawable extends Drawable implements Drawable.Callback {

    static class CompositeState extends Drawable.ConstantState {

        final ArrayList<ChildDrawable> mChildren;

        CompositeState() {
            mChildren = new ArrayList<ChildDrawable>();
        }

        CompositeState(CompositeState other, CompositeDrawable parent, Resources res) {
            final int n = other.mChildren.size();
            mChildren = new ArrayList<ChildDrawable>(n);
            for (int k = 0; k < n; k++) {
                mChildren.add(new ChildDrawable(other.mChildren.get(k), parent, res));
            }
        }

        @NonNull
        @Override
        public Drawable newDrawable() {
            return new CompositeDrawable(this);
        }

        @Override
        public int getChangingConfigurations() {
            return 0;
        }

    }

    CompositeState mState;
    boolean mMutated = false;

    public CompositeDrawable() {
        mState = new CompositeState();
    }

    CompositeDrawable(CompositeState state) {
        mState = state;
    }

    @Override
    public ConstantState getConstantState() {
        return mState;
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mState = new CompositeState(mState, this, null);
            final ArrayList<ChildDrawable> children = mState.mChildren;
            for (int i = 0, n = children.size(); i < n; i++) {
                final Drawable dr = children.get(i).mDrawable;
                if (dr != null) {
                    dr.mutate();
                }
            }
            mMutated = true;
        }
        return this;
    }

    /**
     * Adds the supplied region.
     */
    public void addChildDrawable(Drawable drawable) {
        mState.mChildren.add(new ChildDrawable(drawable, this));
    }

    /**
     * Returns the {@link Drawable} for the given index.
     */
    public Drawable getDrawable(int index) {
        return mState.mChildren.get(index).mDrawable;
    }

    /**
     * Returns the {@link ChildDrawable} at the given index.
     */
    public ChildDrawable getChildAt(int index) {
        return mState.mChildren.get(index);
    }

    /**
     * Removes the child corresponding to the given index.
     */
    public void removeChild(int index) {
        mState.mChildren.remove(index);
    }

    /**
     * Removes the given region.
     */
    public void removeDrawable(Drawable drawable) {
        final ArrayList<ChildDrawable> children = mState.mChildren;
        for (int i = 0; i < children.size(); i++) {
            if (drawable == children.get(i).mDrawable) {
                children.get(i).mDrawable.setCallback(null);
                children.remove(i);
                return;
            }
        }
    }

    /**
     * Returns the total number of children.
     */
    public int getChildCount() {
        return mState.mChildren.size();
    }

    @Override
    public void draw(Canvas canvas) {
        final ArrayList<ChildDrawable> children = mState.mChildren;
        for (int i = 0; i < children.size(); i++) {
            children.get(i).mDrawable.draw(canvas);
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        updateBounds(bounds);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        final ArrayList<ChildDrawable> children = mState.mChildren;
        for (int i = 0; i < children.size(); i++) {
            children.get(i).mDrawable.setColorFilter(colorFilter);
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

    @Override
    public void setAlpha(int alpha) {
        final ArrayList<ChildDrawable> children = mState.mChildren;
        for (int i = 0; i < children.size(); i++) {
            children.get(i).mDrawable.setAlpha(alpha);
        }
    }

    /**
     * @return Alpha value between 0(inclusive) and 255(inclusive)
     */
    public int getAlpha() {
        final Drawable dr = getFirstNonNullDrawable();
        if (dr != null) {
            return DrawableCompat.getAlpha(dr);
        } else {
            return 0xFF;
        }
    }

    final Drawable getFirstNonNullDrawable() {
        final ArrayList<ChildDrawable> children = mState.mChildren;
        for (int i = 0, n = children.size(); i < n; i++) {
            final Drawable dr = children.get(i).mDrawable;
            if (dr != null) {
                return dr;
            }
        }
        return null;
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }

    /**
     * Updates the bounds based on the {@link BoundsRule}.
     */
    void updateBounds(Rect bounds) {
        final ArrayList<ChildDrawable> children = mState.mChildren;
        for (int i = 0; i < children.size(); i++) {
            ChildDrawable childDrawable = children.get(i);
            childDrawable.updateBounds(bounds);
        }
    }

    /**
     * Wrapper class holding a drawable object and {@link BoundsRule} to update drawable bounds
     * when parent bound changes.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static final class ChildDrawable {
        private final BoundsRule mBoundsRule;
        private final Drawable mDrawable;
        private final Rect adjustedBounds = new Rect();
        final CompositeDrawable mParent;

        public ChildDrawable(Drawable drawable, CompositeDrawable parent) {
            this.mDrawable = drawable;
            this.mParent = parent;
            this.mBoundsRule = new BoundsRule();
            drawable.setCallback(parent);
        }

        ChildDrawable(ChildDrawable orig, CompositeDrawable parent, Resources res) {
            final Drawable dr = orig.mDrawable;
            final Drawable clone;
            if (dr != null) {
                final ConstantState cs = dr.getConstantState();
                if (res != null) {
                    clone = cs.newDrawable(res);
                } else {
                    clone = cs.newDrawable();
                }
                clone.setCallback(parent);
                DrawableCompat.setLayoutDirection(clone, DrawableCompat.getLayoutDirection(dr));
                clone.setBounds(dr.getBounds());
                clone.setLevel(dr.getLevel());
            } else {
                clone = null;
            }
            if (orig.mBoundsRule != null) {
                this.mBoundsRule = new BoundsRule(orig.mBoundsRule);
            } else {
                this.mBoundsRule = new BoundsRule();
            }
            mDrawable = clone;
            mParent = parent;
        }

        /**
         * Returns the instance of {@link BoundsRule}.
         */
        public BoundsRule getBoundsRule() {
            return this.mBoundsRule;
        }

        /**
         * Returns the {@link Drawable}.
         */
        public Drawable getDrawable() {
            return mDrawable;
        }

        /**
         * Updates the bounds based on the {@link BoundsRule}.
         */
        void updateBounds(Rect bounds) {
            mBoundsRule.calculateBounds(bounds, adjustedBounds);
            mDrawable.setBounds(adjustedBounds);
        }

        /**
         * After changing the {@link BoundsRule}, user should call this function
         * for the drawable to recalculate its bounds.
         */
        public void recomputeBounds() {
            updateBounds(mParent.getBounds());
        }

        /**
         * Implementation of {@link Property} for overrideTop attribute.
         */
        public static final Property<CompositeDrawable.ChildDrawable, Integer> TOP_ABSOLUTE =
                new Property<CompositeDrawable.ChildDrawable, Integer>(
                        Integer.class, "absoluteTop") {
            @Override
            public void set(CompositeDrawable.ChildDrawable obj, Integer value) {
                if (obj.getBoundsRule().mTop == null) {
                    obj.getBoundsRule().mTop = BoundsRule.absoluteValue(value);
                } else {
                    obj.getBoundsRule().mTop.setAbsoluteValue(value);
                }

                obj.recomputeBounds();
            }

            @Override
            public Integer get(CompositeDrawable.ChildDrawable obj) {
                if (obj.getBoundsRule().mTop == null) {
                    return obj.mParent.getBounds().top;
                }
                return obj.getBoundsRule().mTop.getAbsoluteValue();
            }
        };

        /**
         * Implementation of {@link Property} for overrideBottom attribute.
         */
        public static final Property<CompositeDrawable.ChildDrawable, Integer> BOTTOM_ABSOLUTE =
                new Property<CompositeDrawable.ChildDrawable, Integer>(
                        Integer.class, "absoluteBottom") {
            @Override
            public void set(CompositeDrawable.ChildDrawable obj, Integer value) {
                if (obj.getBoundsRule().mBottom == null) {
                    obj.getBoundsRule().mBottom = BoundsRule.absoluteValue(value);
                } else {
                    obj.getBoundsRule().mBottom.setAbsoluteValue(value);
                }

                obj.recomputeBounds();
            }

            @Override
            public Integer get(CompositeDrawable.ChildDrawable obj) {
                if (obj.getBoundsRule().mBottom == null) {
                    return obj.mParent.getBounds().bottom;
                }
                return obj.getBoundsRule().mBottom.getAbsoluteValue();
            }
        };


        /**
         * Implementation of {@link Property} for overrideLeft attribute.
         */
        public static final Property<CompositeDrawable.ChildDrawable, Integer> LEFT_ABSOLUTE =
                new Property<CompositeDrawable.ChildDrawable, Integer>(
                        Integer.class, "absoluteLeft") {
            @Override
            public void set(CompositeDrawable.ChildDrawable obj, Integer value) {
                if (obj.getBoundsRule().mLeft == null) {
                    obj.getBoundsRule().mLeft = BoundsRule.absoluteValue(value);
                } else {
                    obj.getBoundsRule().mLeft.setAbsoluteValue(value);
                }

                obj.recomputeBounds();
            }

            @Override
            public Integer get(CompositeDrawable.ChildDrawable obj) {
                if (obj.getBoundsRule().mLeft == null) {
                    return obj.mParent.getBounds().left;
                }
                return obj.getBoundsRule().mLeft.getAbsoluteValue();
            }
        };

        /**
         * Implementation of {@link Property} for overrideRight attribute.
         */
        public static final Property<CompositeDrawable.ChildDrawable, Integer> RIGHT_ABSOLUTE =
                new Property<CompositeDrawable.ChildDrawable, Integer>(
                        Integer.class, "absoluteRight") {
            @Override
            public void set(CompositeDrawable.ChildDrawable obj, Integer value) {
                if (obj.getBoundsRule().mRight == null) {
                    obj.getBoundsRule().mRight = BoundsRule.absoluteValue(value);
                } else {
                    obj.getBoundsRule().mRight.setAbsoluteValue(value);
                }

                obj.recomputeBounds();
            }

            @Override
            public Integer get(CompositeDrawable.ChildDrawable obj) {
                if (obj.getBoundsRule().mRight == null) {
                    return obj.mParent.getBounds().right;
                }
                return obj.getBoundsRule().mRight.getAbsoluteValue();
            }
        };

        /**
         * Implementation of {@link Property} for overwriting the bottom attribute of
         * {@link BoundsRule} associated with this {@link ChildDrawable}. This allows users to
         * change the bounds rules as a percentage of parent size. This is preferable over
         * {@see PROPERTY_TOP_ABSOLUTE} when the exact start/end position of scroll movement
         * isn't available at compile time.
         */
        public static final Property<CompositeDrawable.ChildDrawable, Float> TOP_FRACTION =
                new Property<CompositeDrawable.ChildDrawable, Float>(Float.class, "fractionTop") {
            @Override
            public void set(CompositeDrawable.ChildDrawable obj, Float value) {
                if (obj.getBoundsRule().mTop == null) {
                    obj.getBoundsRule().mTop = BoundsRule.inheritFromParent(value);
                } else {
                    obj.getBoundsRule().mTop.setFraction(value);
                }

                obj.recomputeBounds();
            }

            @Override
            public Float get(CompositeDrawable.ChildDrawable obj) {
                if (obj.getBoundsRule().mTop == null) {
                    return 0f;
                }
                return obj.getBoundsRule().mTop.getFraction();
            }
        };

        /**
         * Implementation of {@link Property} for overwriting the bottom attribute of
         * {@link BoundsRule} associated with this {@link ChildDrawable}. This allows users to
         * change the bounds rules as a percentage of parent size. This is preferable over
         * {@see PROPERTY_BOTTOM_ABSOLUTE} when the exact start/end position of scroll movement
         * isn't available at compile time.
         */
        public static final Property<CompositeDrawable.ChildDrawable, Float> BOTTOM_FRACTION =
                new Property<CompositeDrawable.ChildDrawable, Float>(
                        Float.class, "fractionBottom") {
            @Override
            public void set(CompositeDrawable.ChildDrawable obj, Float value) {
                if (obj.getBoundsRule().mBottom == null) {
                    obj.getBoundsRule().mBottom = BoundsRule.inheritFromParent(value);
                } else {
                    obj.getBoundsRule().mBottom.setFraction(value);
                }

                obj.recomputeBounds();
            }

            @Override
            public Float get(CompositeDrawable.ChildDrawable obj) {
                if (obj.getBoundsRule().mBottom == null) {
                    return 1f;
                }
                return obj.getBoundsRule().mBottom.getFraction();
            }
        };

        /**
         * Implementation of {@link Property} for overwriting the bottom attribute of
         * {@link BoundsRule} associated with this {@link ChildDrawable}. This allows users to
         * change the bounds rules as a percentage of parent size. This is preferable over
         * {@see PROPERTY_LEFT_ABSOLUTE} when the exact start/end position of scroll movement
         * isn't available at compile time.
         */
        public static final Property<CompositeDrawable.ChildDrawable, Float> LEFT_FRACTION =
                new Property<CompositeDrawable.ChildDrawable, Float>(Float.class, "fractionLeft") {
            @Override
            public void set(CompositeDrawable.ChildDrawable obj, Float value) {
                if (obj.getBoundsRule().mLeft == null) {
                    obj.getBoundsRule().mLeft = BoundsRule.inheritFromParent(value);
                } else {
                    obj.getBoundsRule().mLeft.setFraction(value);
                }

                obj.recomputeBounds();
            }

            @Override
            public Float get(CompositeDrawable.ChildDrawable obj) {
                if (obj.getBoundsRule().mLeft == null) {
                    return 0f;
                }
                return obj.getBoundsRule().mLeft.getFraction();
            }
        };

        /**
         * Implementation of {@link Property} for overwriting the bottom attribute of
         * {@link BoundsRule} associated with this {@link ChildDrawable}. This allows users to
         * change the bounds rules as a percentage of parent size. This is preferable over
         * {@see PROPERTY_RIGHT_ABSOLUTE} when the exact start/end position of scroll movement
         * isn't available at compile time.
         */
        public static final Property<CompositeDrawable.ChildDrawable, Float> RIGHT_FRACTION =
                new Property<CompositeDrawable.ChildDrawable, Float>(Float.class, "fractoinRight") {
            @Override
            public void set(CompositeDrawable.ChildDrawable obj, Float value) {
                if (obj.getBoundsRule().mRight == null) {
                    obj.getBoundsRule().mRight = BoundsRule.inheritFromParent(value);
                } else {
                    obj.getBoundsRule().mRight.setFraction(value);
                }

                obj.recomputeBounds();
            }

            @Override
            public Float get(CompositeDrawable.ChildDrawable obj) {
                if (obj.getBoundsRule().mRight == null) {
                    return 1f;
                }
                return obj.getBoundsRule().mRight.getFraction();
            }
        };
    }
}
