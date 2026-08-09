import streamlit as st
import pandas as pd
from db import create_plan, add_plan, view_plan, delete_plan, view_claims_admin, get_plan_using_id, update_plan

def plan(adminId):

    st.title("Insurance Plans")

    menu = [ "Create Plan", "View Plans", "Delete plan"]

    choice = st.selectbox("Choose :", menu)

    if choice == 'Create Plan':
        st.subheader("Create Plan")
        st.markdown('***')

        plan_name = st.text_input("Plan Name:")
        plan_description = st.text_input("Plan Description:")
        plan_price = st.text_input("Plan Price:")
        plan_duration = st.text_input("Plan Duration:")

        if st.button('Create Plan'):
            # st.write(adminId)
            create_plan()
            add_plan(plan_name, plan_description, plan_price, plan_duration, int(adminId))
            st.success("Plan Created Successfully")

    elif choice == 'View Plans':
        st.subheader("View Plans")
        st.markdown('***')
        data = view_plan(int(adminId))
        # st.write(data)
        if data:
            df = pd.DataFrame(data, columns=[
                                'ID', 'Plan Name', 'Plan Description', 'Plan Price', 'Plan Duration', 'Admin Id'])
            # with st.expander("View Plans"):
            st.dataframe(df)

            st.subheader("Edit plan")
            list_of_plans = [i[0] for i in data]
            selected_plan_id = st.selectbox("Choose Plan to edit :", list_of_plans)

            st.warning("Are you sure you want to update plan : {}?".format(selected_plan_id))
            if st.checkbox("Update plan {}".format(selected_plan_id)):
                plan_details = get_plan_using_id(int(selected_plan_id))
                if plan_details:
                    old_plan_name = plan_details[0][1]
                    old_plan_description = plan_details[0][2]
                    old_plan_price = plan_details[0][3]
                    old_plan_duration = plan_details[0][4]
                    
                    new_plan_name = st.text_input("Plan name", old_plan_name)
                    new_plan_description = st.text_input("Plan description", old_plan_description)
                    new_plan_price = st.text_input("Plan price", old_plan_price)
                    new_plan_duration = st.text_input("Plan duration", old_plan_duration)

                    if st.checkbox("Update"):
                        update_plan(new_plan_name, new_plan_description, new_plan_price, new_plan_duration, int(selected_plan_id))
                        st.success("Plan details updated successfully")
            
    elif choice == 'Delete plan':
        st.subheader("Delete plan")
        st.markdown('***')
        result = view_plan(int(adminId))
        df = pd.DataFrame(result, columns=[
                            'ID', 'Plan Name', 'Plan Description', 'Plan Price', 'Plan Duration', 'Admin Id'])

        list_of_plans = [i[0] for i in result]

        # st.write(list_of_plans)
        if len(list_of_plans) > 0:

            selected_plan_id = st.selectbox("Choose Plan to Delete:", list_of_plans)

            st.warning("Are you sure you want to delete plan : {}?".format(selected_plan_id))

            if st.button('Delete'):
                delete_plan(selected_plan_id)
                st.success("Plan Deleted Successfully")

            new_result = view_plan(int(adminId))
            new_df = pd.DataFrame(new_result, columns=[
                                'ID', 'Plan Name', 'Plan Description', 'Plan Price', 'Plan Duration', 'Admin Id'])

            with st.expander("Updated Plans"):
                st.dataframe(new_df)
        
        else: 
            st.warning("No plans to delete")
